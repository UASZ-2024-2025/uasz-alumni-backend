package com.uasz.alumni.user.service;

import com.uasz.alumni.user.exceptions.BadRequestException;
import com.uasz.alumni.user.exceptions.ConflictException;
import com.uasz.alumni.user.exceptions.ForbiddenException;
import com.uasz.alumni.user.exceptions.ResourceNotFoundException;
import com.uasz.alumni.user.model.UserEntity;
import com.uasz.alumni.user.repository.UserRepository;
import com.uasz.alumni.user.security.SecurityUtils;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserPagedResponse;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.spi.model.UserStatusUpdateRequest;
import com.uasz.alumni.user.spi.model.UserUpdateRequest;
import com.uasz.alumni.user.utils.UserMapper;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPagedResponse listUsers(RoleName role, UserStatus status, Integer page, Integer size) {
        if (!SecurityUtils.isCurrentUserAdmin()) {
            throw new ForbiddenException("Seul un administrateur peut lister tous les utilisateurs.");
        }

        int pageIndex = (page != null && page >= 0) ? page : 0;
        int pageSize = (size != null && size > 0) ? size : 20;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<UserEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role").get("name"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<UserEntity> result = userRepository.findAll(spec, pageable);
        return UserMapper.toUserPagedResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(UUID id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant utilisateur est requis.");
        }

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable avec l'identifiant : " + id));

        return UserMapper.toUser(user);
    }

    @Override
    public User updateUser(UUID id, UserUpdateRequest request) {
        if (id == null) {
            throw new BadRequestException("L'identifiant utilisateur est requis.");
        }
        if (request == null) {
            throw new BadRequestException("Les données de mise à jour sont requises.");
        }

        // Règle métier : Un utilisateur peut modifier ses propres informations, ou l'ADMINISTRATEUR
        boolean isAdmin = SecurityUtils.isCurrentUserAdmin();
        boolean isOwner = SecurityUtils.isCurrentUserId(id);

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier ce compte utilisateur.");
        }

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable avec l'identifiant : " + id));

        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender().name());
        }
        if (request.getBirthDate() != null) {
            user.setBirthDate(request.getBirthDate());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (userRepository.existsByEmail(newEmail)) {
                throw new ConflictException("Cette adresse email est déjà utilisée : " + newEmail);
            }
            user.setEmail(newEmail);
        }

        UserEntity updated = userRepository.save(user);
        log.info("Compte utilisateur mis à jour avec succès : id={}, email={}", updated.getId(), updated.getEmail());
        return UserMapper.toUser(updated);
    }

    /**
     * Supprime un compte utilisateur.
     *
     * EFFET D'UNE SUPPRESSION SUR LES AUTRES SERVICES (Architecture Microservices & RGPD) :
     * -----------------------------------------------------------------------------------
     * Dans un écosystème Database-per-service découplé, la suppression d'un compte utilisateur
     * dans user-service déclenche une politique d'anonymisation et de purge inter-services :
     * 1. alumni-student-company-service :
     *    - Le profil étudiant (StudentProfile) ou alumni (AlumniProfile) et le CV associé
     *      sont supprimés ou anonymisés.
     * 2. job-offer-service :
     *    - Les candidatures (applications) déposées sont archivées avec le statut REJETEE ou
     *      anonymisées ("Candidat supprimé") afin de préserver l'historique de l'entreprise.
     *    - Les offres publiées par cet utilisateur (si recruteur/alumni) sont clôturées (status CLOTUREE).
     * 3. notification-messaging-service :
     *    - Les messages privés sont conservés pour l'interlocuteur avec un auteur anonymisé
     *      "Compte supprimé" (respect du droit à l'effacement RGPD sans détruire la boîte de l'autre).
     *    - Les notifications non lues sont purgées.
     *    - L'utilisateur est retiré de tous les groupes de discussion.
     */
    @Override
    public void deleteUser(UUID id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant utilisateur est requis.");
        }

        // Règle métier : Réservé à l'administrateur
        if (!SecurityUtils.isCurrentUserAdmin()) {
            throw new ForbiddenException("Seul un administrateur peut supprimer un compte.");
        }

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable avec l'identifiant : " + id));

        userRepository.delete(user);
        log.info("Compte utilisateur supprimé par l'administrateur : id={}, email={}", id, user.getEmail());
    }

    @Override
    public User updateUserStatus(UUID id, UserStatusUpdateRequest request) {
        if (id == null) {
            throw new BadRequestException("L'identifiant utilisateur est requis.");
        }
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("Le statut utilisateur est obligatoire.");
        }

        // Règle métier : Réservé strictement à l'ADMINISTRATEUR (403 sinon)
        if (!SecurityUtils.isCurrentUserAdmin()) {
            throw new ForbiddenException("Action réservée à l'Administrateur");
        }

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable avec l'identifiant : " + id));

        UserStatus previousStatus = user.getStatus();
        user.setStatus(request.getStatus());

        UserEntity updated = userRepository.save(user);
        log.info("Statut utilisateur modifié par l'administrateur : id={}, ancienStatut={}, nouveauStatut={}",
                id, previousStatus, updated.getStatus());

        return UserMapper.toUser(updated);
    }
}

