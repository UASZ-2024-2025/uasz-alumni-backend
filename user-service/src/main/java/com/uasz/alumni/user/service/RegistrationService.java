package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AppProperties;
import com.uasz.alumni.user.exceptions.AccountAlreadyActiveException;
import com.uasz.alumni.user.exceptions.EmailAlreadyUsedException;
import com.uasz.alumni.user.exceptions.InvalidOrExpiredTokenException;
import com.uasz.alumni.user.exceptions.RoleNotAllowedException;
import com.uasz.alumni.user.exceptions.WeakPasswordException;
import com.uasz.alumni.user.model.EmailConfirmationToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.EmailConfirmationTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.PasswordPolicy;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AL-18 : inscription, confirmation de l'adresse email et renvoi du lien de confirmation. */
@Service
public class RegistrationService {

    /** Rôles accessibles à l'auto-inscription. ADMINISTRATEUR est attribué par un administrateur, jamais ici. */
    static final Set<RoleName> SELF_REGISTRATION_ROLES =
            EnumSet.of(RoleName.ALUMNI, RoleName.ETUDIANT, RoleName.ENSEIGNANT, RoleName.ENTREPRISE);

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
        if (!PasswordPolicy.isValid(request.getPassword())) {
            throw new WeakPasswordException(HttpStatus.BAD_REQUEST);
        }
        String email = normalizeEmail(request.getEmail());
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
            throw new EmailAlreadyUsedException();
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

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
