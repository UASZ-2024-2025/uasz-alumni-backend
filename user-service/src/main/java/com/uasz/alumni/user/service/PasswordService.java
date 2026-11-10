package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AppProperties;
import com.uasz.alumni.user.config.AuthProperties;
import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.InvalidCurrentPasswordException;
import com.uasz.alumni.user.exceptions.InvalidResetTokenException;
import com.uasz.alumni.user.exceptions.UserNotFoundException;
import com.uasz.alumni.user.exceptions.WeakPasswordException;
import com.uasz.alumni.user.model.PasswordResetToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.PasswordResetTokenRepository;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.PasswordPolicy;
import com.uasz.alumni.user.utils.TextUtils;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AL-20 : mot de passe oublié, réinitialisation par jeton, changement de mot de passe, invitation d'un compte. */
@Service
public class PasswordService {

    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final AuthProperties authProps;
    private final AppProperties appProps;
    private final AuditService audit;
    private final Clock clock;

    public PasswordService(UserAccountRepository users, PasswordResetTokenRepository resetTokens,
                           RefreshTokenService refreshTokens, PasswordEncoder encoder,
                           ApplicationEventPublisher events, AuthProperties authProps, AppProperties appProps,
                           AuditService audit, Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.events = events;
        this.authProps = authProps;
        this.appProps = appProps;
        this.audit = audit;
        this.clock = clock;
    }

    /** Réponse identique que le compte existe ou non. Comptes INACTIF/SUSPENDU et demandes trop rapprochées : ignorés. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        Instant now = clock.instant();
        users.findByEmail(TextUtils.normalizeEmail(rawEmail)).ifPresent(user -> {
            if (user.getStatus() == UserStatus.INACTIF || user.getStatus() == UserStatus.SUSPENDU) {
                return;
            }
            boolean tooSoon = resetTokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                    .map(t -> t.getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now))
                    .orElse(false);
            if (tooSoon) {
                return;
            }
            issueToken(user, authProps.passwordResetTtl(), PasswordEmailRequested.Kind.RESET);
            audit.record("PASSWORD_RESET_REQUESTED", user.getId(), user.getId(), null);
        });
    }

    /** Compte créé par un administrateur : envoie le lien « définir mon mot de passe » (valable comme la confirmation d'email). */
    @Transactional
    public void sendInvitation(UserAccount user) {
        issueToken(user, appProps.confirmationTtl(), PasswordEmailRequested.Kind.INVITATION);
    }

    /**
     * Consomme le jeton, pose le nouveau mot de passe, active un compte EN_ATTENTE (la réception du lien
     * prouve le contrôle de l'adresse), lève un éventuel verrouillage et ferme toutes les sessions.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHashWithUser(TokenUtils.sha256Hex(rawToken.trim()))
                .orElseThrow(InvalidResetTokenException::new);
        UserAccount user = token.getUser();

        if (token.isUsed() || token.isExpiredAt(now)
                || user.getStatus() == UserStatus.INACTIF || user.getStatus() == UserStatus.SUSPENDU) {
            throw new InvalidResetTokenException();
        }
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new WeakPasswordException(HttpStatus.valueOf(422));
        }

        token.markUsed(now);
        user.changePasswordHash(encoder.encode(newPassword));
        if (user.getStatus() == UserStatus.EN_ATTENTE) {
            user.activate(now);
        }
        user.clearLock();
        refreshTokens.revokeAll(user.getId());
        events.publishEvent(new PasswordChangedNotice(user.getEmail(), user.getFirstName()));
        audit.record("PASSWORD_RESET", user.getId(), user.getId(), null);
    }

    /** Utilisateur connecté. Toutes ses sessions sont fermées : il doit se reconnecter. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new WeakPasswordException(HttpStatus.valueOf(422));
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED",
                    "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        user.changePasswordHash(encoder.encode(newPassword));
        refreshTokens.revokeAll(userId);
        events.publishEvent(new PasswordChangedNotice(user.getEmail(), user.getFirstName()));
        audit.record("PASSWORD_CHANGED", userId, userId, null);
    }

    private void issueToken(UserAccount user, Duration ttl, PasswordEmailRequested.Kind kind) {
        resetTokens.deleteAllByUserId(user.getId());

        Instant now = clock.instant();
        String rawToken = TokenUtils.generateToken();
        resetTokens.save(new PasswordResetToken(user, TokenUtils.sha256Hex(rawToken), now, now.plus(ttl)));

        events.publishEvent(new PasswordEmailRequested(user.getEmail(), user.getFirstName(), rawToken, kind, ttl));
    }
}
