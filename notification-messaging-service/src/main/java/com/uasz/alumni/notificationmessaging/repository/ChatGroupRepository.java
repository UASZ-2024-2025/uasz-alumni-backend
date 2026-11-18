package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.ChatGroup;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, UUID> {

    /** Verrou de ligne : sérialise les changements de membres et de rôles d'un même groupe (propriétaire unique). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ChatGroup g where g.id = :id")
    Optional<ChatGroup> findByIdForUpdate(UUID id);
}
