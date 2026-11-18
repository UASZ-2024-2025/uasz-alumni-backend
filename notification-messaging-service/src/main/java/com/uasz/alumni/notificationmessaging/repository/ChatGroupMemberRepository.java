package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.ChatGroupMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatGroupMemberRepository extends JpaRepository<ChatGroupMember, UUID> {

    /** Membres d'un groupe, du plus ancien au plus récent (ordre de passation de la propriété). */
    List<ChatGroupMember> findByGroupIdOrderByJoinedAtAsc(UUID groupId);

    Optional<ChatGroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    List<ChatGroupMember> findByUserId(UUID userId);

    long countByGroupId(UUID groupId);
}
