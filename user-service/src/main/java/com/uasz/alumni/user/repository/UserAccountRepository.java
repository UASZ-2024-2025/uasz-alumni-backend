package com.uasz.alumni.user.repository;

import com.uasz.alumni.user.model.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** L'email est toujours stocké en minuscules : voir {@code RegistrationService}. */
    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);
}
