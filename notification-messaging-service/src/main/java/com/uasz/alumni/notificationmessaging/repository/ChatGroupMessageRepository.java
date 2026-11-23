package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.ChatGroupMessage;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatGroupMessageRepository extends JpaRepository<ChatGroupMessage, UUID> {

    /** Du plus récent au plus ancien : la page 0 contient les derniers messages. */
    Page<ChatGroupMessage> findByGroupIdOrderByCreatedAtDescSeqDesc(UUID groupId, Pageable pageable);

    Optional<ChatGroupMessage> findFirstByGroupIdOrderByCreatedAtDescSeqDesc(UUID groupId);

    Optional<ChatGroupMessage> findByIdAndGroupId(UUID id, UUID groupId);

    /** Messages à pièce jointe d'un groupe (fichiers à effacer quand le groupe disparaît). */
    java.util.List<ChatGroupMessage> findByGroupIdAndAttachmentPublicIdIsNotNull(UUID groupId);

    /** Non-lus : messages TEXT non supprimés des autres membres, postérieurs à la dernière lecture. */
    @Query("""
            select count(m) from ChatGroupMessage m
            where m.groupId = :groupId and m.senderId <> :userId and m.createdAt > :since
              and m.type = com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType.TEXT and m.deleted = false
            """)
    long countUnread(UUID groupId, UUID userId, OffsetDateTime since);
}
