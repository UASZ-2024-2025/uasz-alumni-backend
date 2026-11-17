package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** L'email est toujours stocké en minuscules : voir {@code TextUtils.normalizeEmail}. */
    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);

    long countByRoleAndStatus(RoleName role, UserStatus status);

    Page<UserAccount> findByRole(RoleName role, Pageable pageable);

    Page<UserAccount> findByStatus(UserStatus status, Pageable pageable);

    Page<UserAccount> findByRoleAndStatus(RoleName role, UserStatus status, Pageable pageable);
}
