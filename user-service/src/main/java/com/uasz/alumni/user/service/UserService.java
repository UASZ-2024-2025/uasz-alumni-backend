package com.uasz.alumni.user.service;

import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.EmailAlreadyUsedException;
import com.uasz.alumni.user.exceptions.ForbiddenException;
import com.uasz.alumni.user.exceptions.UserNotFoundException;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.security.CurrentUser;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserCreateRequest;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import com.uasz.alumni.user.utils.TextUtils;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des utilisateurs. Les routes réservées à l'ADMINISTRATEUR sont aussi verrouillées dans SecurityConfig. */
@Service
public class UserService {

    private final UserAccountRepository users;
    private final RefreshTokenService refreshTokens;
    private final PasswordService passwords;
    private final PasswordEncoder encoder;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final Clock clock;

    public UserService(UserAccountRepository users, RefreshTokenService refreshTokens, PasswordService passwords,
                       PasswordEncoder encoder, CurrentUser currentUser, AuditService audit, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.encoder = encoder;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> list(RoleName role, UserStatus status, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(
                page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        if (role != null && status != null) {
            return users.findByRoleAndStatus(role, status, pageable);
        }
        if (role != null) {
            return users.findByRole(role, pageable);
        }
        if (status != null) {
            return users.findByStatus(status, pageable);
        }
        return users.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public UserAccount get(UUID id) {
        currentUser.requireSelfOrAdmin(id);
        return load(id);
    }

    /** Création par un ADMINISTRATEUR : mot de passe aléatoire inconnu de tous, l'utilisateur le définit via le lien reçu. */
    @Transactional
    public UserAccount create(UserCreateRequest request) {
        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            throw badRequest("firstName ne peut pas être vide.");
        }
        if (request.getLastName() == null || request.getLastName().isBlank()) {
            throw badRequest("lastName ne peut pas être vide.");
        }
        String email = TextUtils.normalizeEmail(request.getEmail());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        UserAccount account = new UserAccount(
                email,
                encoder.encode(TokenUtils.generateToken()),
                request.getFirstName().trim(),
                request.getLastName().trim(),
                request.getRole(),
                UserStatus.EN_ATTENTE);
        if (request.getPhone() != null) {
            account.updateProfile(null, null, request.getPhone().trim(), null, null);
        }
        try {
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
        passwords.sendInvitation(account);
        audit.record("USER_CREATED", currentUser.require().id(), account.getId(), "role=" + account.getRole());
        return account;
    }

    /** Soi-même ou ADMINISTRATEUR. Mise à jour partielle. L'email n'est modifiable que par un ADMINISTRATEUR. */
    @Transactional
    public UserAccount update(UUID id, UserUpdateRequest r) {
        currentUser.requireSelfOrAdmin(id);
        UserAccount account = load(id);

        if (r.getFirstName() != null && r.getFirstName().isBlank()) {
            throw badRequest("firstName ne peut pas être vide.");
        }
        if (r.getLastName() != null && r.getLastName().isBlank()) {
            throw badRequest("lastName ne peut pas être vide.");
        }
        if (r.getPhone() != null && r.getPhone().trim().length() > 30) {
            throw badRequest("phone : 30 caractères maximum.");
        }
        if (r.getBirthDate() != null && !r.getBirthDate().isBefore(LocalDate.now(clock))) {
            throw badRequest("birthDate doit être dans le passé.");
        }

        if (r.getEmail() != null) {
            String newEmail = TextUtils.normalizeEmail(r.getEmail());
            if (!newEmail.equals(account.getEmail())) {
                if (!currentUser.isAdmin()) {
                    throw new ForbiddenException("Seul un administrateur peut modifier l'adresse email.");
                }
                if (users.existsByEmail(newEmail)) {
                    throw new EmailAlreadyUsedException();
                }
                account.changeEmail(newEmail);
            }
        }

        account.updateProfile(
                r.getFirstName() == null ? null : r.getFirstName().trim(),
                r.getLastName() == null ? null : r.getLastName().trim(),
                r.getPhone() == null ? null : r.getPhone().trim(),
                r.getGender(),
                r.getBirthDate());
        try {
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
        audit.record("USER_UPDATED", currentUser.require().id(), id, null);
        return account;
    }

    @Transactional
    public UserAccount updateStatus(UUID id, UserStatus newStatus) {
        UUID actorId = currentUser.require().id();
        UserAccount account = load(id);

        if (account.getId().equals(actorId)) {
            throw selfModification();
        }
        if (newStatus == null || newStatus == UserStatus.EN_ATTENTE) {
            throw badRequest("Statut invalide : seuls ACTIF, INACTIF et SUSPENDU peuvent être attribués.");
        }
        if (newStatus != account.getStatus()) {
            if (newStatus != UserStatus.ACTIF) {
                guardLastAdmin(account);
            }
            account.changeStatus(newStatus);
            if (newStatus == UserStatus.ACTIF) {
                account.clearLock();
            } else {
                refreshTokens.revokeAll(id);
            }
            audit.record("USER_STATUS_CHANGED", actorId, id, "status=" + newStatus);
        }
        return account;
    }

    @Transactional
    public void delete(UUID id) {
        UUID actorId = currentUser.require().id();
        UserAccount account = load(id);

        if (account.getId().equals(actorId)) {
            throw selfModification();
        }
        guardLastAdmin(account);
        users.delete(account);
        audit.record("USER_DELETED", actorId, id, "email=" + account.getEmail());
    }

    // ───── privé ─────

    private UserAccount load(UUID id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }

    /** Interdit de désactiver, suspendre ou supprimer le dernier ADMINISTRATEUR actif. */
    private void guardLastAdmin(UserAccount target) {
        if (target.getRole() == RoleName.ADMINISTRATEUR && target.getStatus() == UserStatus.ACTIF
                && users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN",
                    "Opération impossible : il doit rester au moins un administrateur actif.");
        }
    }

    private static ApiException selfModification() {
        return new ApiException(HttpStatus.CONFLICT, "CANNOT_MODIFY_SELF",
                "Vous ne pouvez pas effectuer cette action sur votre propre compte.");
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
