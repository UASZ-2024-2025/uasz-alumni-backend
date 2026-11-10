package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AuthProperties;
import com.uasz.alumni.user.exceptions.AccountDisabledException;
import com.uasz.alumni.user.exceptions.AccountLockedException;
import com.uasz.alumni.user.exceptions.AccountNotActivatedException;
import com.uasz.alumni.user.exceptions.ApiException;
import com.uasz.alumni.user.exceptions.InvalidCredentialsException;
import com.uasz.alumni.user.exceptions.InvalidRefreshTokenException;
import com.uasz.alumni.user.jwt.JwtService;
import com.uasz.alumni.user.model.RefreshToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.TextUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AL-19 : connexion, rafraîchissement du jeton, déconnexion. */
@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final AuthProperties props;
    private final Clock clock;

    /** Hash factice : un email inconnu coûte le même temps de calcul qu'un mauvais mot de passe. */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwt,
                       RefreshTokenService refreshTokens, AuditService audit, AuthProperties props, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * noRollbackFor : le compteur d'échecs et l'audit doivent être enregistrés même quand la méthode
     * se termine par une ApiException (401/403/423).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession login(String rawEmail, String password) {
        Instant now = clock.instant();
        String email = TextUtils.normalizeEmail(rawEmail);

        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            audit.record("LOGIN_FAILED", null, null, "email=" + email);
            throw new InvalidCredentialsException();
        }
        if (user.isLockedAt(now)) {
            audit.record("LOGIN_LOCKED", user.getId(), user.getId(), null);
            throw new AccountLockedException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(props.maxFailedAttempts(), props.lockDuration(), now);
            audit.record("LOGIN_FAILED", user.getId(), user.getId(), null);
            throw new InvalidCredentialsException();
        }

        // Le statut n'est révélé qu'à quelqu'un qui connaît le bon mot de passe.
        switch (user.getStatus()) {
            case EN_ATTENTE -> throw new AccountNotActivatedException();
            case INACTIF, SUSPENDU -> throw new AccountDisabledException();
            case ACTIF -> { }
        }

        user.registerSuccessfulLogin(now);
        audit.record("LOGIN", user.getId(), user.getId(), null);
        return session(user, refreshTokens.issue(user));
    }

    /**
     * Rotation : l'ancien refresh token est révoqué et un nouveau est émis. Présenter un jeton DÉJÀ
     * révoqué indique une réutilisation (vol possible) : toutes les sessions de l'utilisateur sont fermées.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.find(rawRefreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        UserAccount user = token.getUser();

        if (token.isRevoked()) {
            refreshTokens.revokeAll(user.getId());
            audit.record("REFRESH_REUSE_DETECTED", user.getId(), user.getId(), null);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpiredAt(now) || user.getStatus() != UserStatus.ACTIF || user.isLockedAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        token.revoke(now);
        audit.record("TOKEN_REFRESHED", user.getId(), user.getId(), null);
        return session(user, refreshTokens.issue(user));
    }

    /** L'access token (stateless) expire seul ; on révoque le refresh token fourni. */
    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        refreshTokens.revoke(userId, rawRefreshToken);
        audit.record("LOGOUT", userId, userId, null);
    }

    private AuthSession session(UserAccount user, String rawRefreshToken) {
        return new AuthSession(jwt.generateAccessToken(user), rawRefreshToken, jwt.accessTtlSeconds(), user);
    }
}
