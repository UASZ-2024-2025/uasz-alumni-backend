package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.EmailConfirmationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, UUID> {

    @Query("select t from EmailConfirmationToken t join fetch t.user where t.tokenHash = :hash")
    Optional<EmailConfirmationToken> findByTokenHashWithUser(@Param("hash") String hash);

    /** Invalide les anciens jetons d'un utilisateur avant d'en émettre un nouveau. */
    @Modifying(flushAutomatically = true)
    @Query("delete from EmailConfirmationToken t where t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
