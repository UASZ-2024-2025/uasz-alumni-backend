package com.uasz.alumni.user.service;

import com.uasz.alumni.user.config.AuthProperties;
import com.uasz.alumni.user.model.RefreshToken;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.RefreshTokenRepository;
import com.uasz.alumni.user.utils.TokenUtils;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Émission, recherche et révocation des refresh tokens (jamais stockés en clair). */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final AuthProperties props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repo, AuthProperties props, Clock clock) {
        this.repo = repo;
        this.props = props;
        this.clock = clock;
    }

    /** Crée un refresh token et retourne sa valeur EN CLAIR (à renvoyer au client, jamais relue ensuite). */
    @Transactional
    public String issue(UserAccount user) {
        Instant now = clock.instant();
        String raw = TokenUtils.generateToken();
        repo.save(new RefreshToken(user, TokenUtils.sha256Hex(raw), now, now.plus(props.refreshTtl())));
        return raw;
    }

    public Optional<RefreshToken> find(String rawToken) {
        return repo.findByTokenHashWithUser(TokenUtils.sha256Hex(rawToken.trim()));
    }

    /** Révoque ce jeton s'il appartient bien à l'utilisateur ; sinon ignore sans erreur. */
    @Transactional
    public void revoke(UUID userId, String rawToken) {
        Instant now = clock.instant();
        find(rawToken)
                .filter(t -> t.getUser().getId().equals(userId))
                .ifPresent(t -> t.revoke(now));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repo.revokeAllByUserId(userId, clock.instant());
    }
}
