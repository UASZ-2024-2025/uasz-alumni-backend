package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AppProperties;
import com.uasz.alumni.user.exceptions.AccountAlreadyActiveException;
import com.uasz.alumni.user.exceptions.EmailAlreadyUsedException;
import com.uasz.alumni.user.exceptions.InvalidOrExpiredTokenException;
import com.uasz.alumni.user.exceptions.InvalidRegistrationDataException;
import com.uasz.alumni.user.exceptions.RoleNotAllowedException;
import com.uasz.alumni.user.model.EmailConfirmationToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.EmailConfirmationTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.TokenUtils;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AL-18 : inscription, confirmation de l'adresse email et renvoi du lien de confirmation. */
@Service
public class RegistrationService {

    /** Rôles accessibles à l'auto-inscription. ADMINISTRATEUR est attribué par un administrateur, jamais ici. */
    static final Set<RoleName> SELF_REGISTRATION_ROLES =
            EnumSet.of(RoleName.ALUMNI, RoleName.ETUDIANT, RoleName.ENSEIGNANT);

    private static final int NAME_MAX_LENGTH = 100;
    private static final int EMAIL_MAX_LENGTH = 255;
    /** BCrypt ne lit que 72 octets : au-delà, Spring Security refuse le mot de passe (erreur 500 sinon). */
    static final int PASSWORD_MAX_BYTES = 72;
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

    private final UserAccountRepository users;
    private final EmailConfirmationTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    public RegistrationService(UserAccountRepository users,
                               EmailConfirmationTokenRepository tokens,
                               PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher events,
                               AppProperties properties,
                               Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** Crée le compte au statut EN_ATTENTE et déclenche l'envoi du lien de confirmation. */
    @Transactional
    public UserAccount register(RegisterRequest request) {
        if (!SELF_REGISTRATION_ROLES.contains(request.getRole())) {
            throw new RoleNotAllowedException();
        }
        String email = normalizeEmail(request.getEmail());
        validate(request, email);
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        UserAccount account = new UserAccount(
                email,
                passwordEncoder.encode(request.getPassword()),
                request.getFirstName().trim(),
                request.getLastName().trim(),
                request.getRole(),
                UserStatus.EN_ATTENTE);
        try {
            // flush immédiat : une inscription concurrente avec le même email échoue ici (contrainte unique)
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // seule la contrainte d'unicité de l'email signifie « déjà utilisé » ; toute autre violation est une vraie erreur
            if (violatesEmailUniqueness(e)) {
                throw new EmailAlreadyUsedException();
            }
            throw e;
        }

        issueConfirmationToken(account);
        return account;
    }

    /** Active le compte si le jeton est valide, non expiré et non utilisé. */
    @Transactional
    public void confirmEmail(String rawToken) {
        Instant now = clock.instant();
        EmailConfirmationToken token = tokens.findByTokenHashWithUser(TokenUtils.sha256Hex(rawToken.trim()))
                .orElseThrow(InvalidOrExpiredTokenException::new);

        UserAccount account = token.getUser();
        if (token.isUsed() || token.isExpiredAt(now) || account.getStatus() != UserStatus.EN_ATTENTE) {
            throw new InvalidOrExpiredTokenException();
        }

        token.markUsed(now);
        account.activate(now);
        tokens.save(token);
        users.save(account);
    }

    /**
     * Renvoie un lien de confirmation. Pour ne pas révéler quelles adresses ont un compte, une adresse
     * inconnue (ou un compte suspendu/inactif) est ignorée sans erreur. Seul un compte déjà actif est
     * signalé (409), comme l'exige le contrat.
     */
    @Transactional
    public void resendConfirmation(String rawEmail) {
        users.findByEmail(normalizeEmail(rawEmail)).ifPresent(account -> {
            if (account.getStatus() == UserStatus.ACTIF) {
                throw new AccountAlreadyActiveException();
            }
            if (account.getStatus() == UserStatus.EN_ATTENTE) {
                issueConfirmationToken(account);
            }
        });
    }

    private void issueConfirmationToken(UserAccount account) {
        tokens.deleteAllByUserId(account.getId());

        Instant now = clock.instant();
        String rawToken = TokenUtils.generateToken();
        tokens.save(new EmailConfirmationToken(
                account,
                TokenUtils.sha256Hex(rawToken),
                now,
                now.plus(properties.confirmationTtl())));

        events.publishEvent(new ConfirmationEmailRequested(account.getEmail(), account.getFirstName(), rawToken));
    }

    /** Refuse en 400 ce que la base rejetterait (colonnes limitées) ou que BCrypt ne sait pas hacher. */
    private static void validate(RegisterRequest request, String email) {
        requireName(request.getFirstName(), "prénom");
        requireName(request.getLastName(), "nom");
        if (email.length() > EMAIL_MAX_LENGTH) {
            throw new InvalidRegistrationDataException(
                    "L'adresse email ne doit pas dépasser " + EMAIL_MAX_LENGTH + " caractères.");
        }
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new InvalidRegistrationDataException(
                    "Le mot de passe ne doit pas dépasser " + PASSWORD_MAX_BYTES + " octets.");
        }
    }

    private static void requireName(String value, String label) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new InvalidRegistrationDataException(
                    "Le " + label + " est obligatoire et ne doit pas dépasser " + NAME_MAX_LENGTH + " caractères.");
        }
    }

    private static boolean violatesEmailUniqueness(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
