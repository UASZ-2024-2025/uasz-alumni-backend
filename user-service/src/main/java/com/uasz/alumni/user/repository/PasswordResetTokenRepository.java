package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHashWithUser(@Param("hash") String hash);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Un seul jeton actif par utilisateur : on invalide les anciens avant d'en émettre un nouveau. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :limit")
    int deleteExpired(@Param("limit") Instant limit);
}
