package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.PrivateMessageEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessageEntity, UUID> {

    boolean existsByAttachmentPublicId(String publicId);

    @Query("""
        select m from PrivateMessageEntity m
        where (m.senderId = :me and m.recipientId = :other)
           or (m.senderId = :other and m.recipientId = :me)
        """)
    Page<PrivateMessageEntity> findConversation(@Param("me") UUID me,
                                                @Param("other") UUID other,
                                                Pageable pageable);

    @Query("""
        select m from PrivateMessageEntity m
        where m.senderId = :me or m.recipientId = :me
        order by m.sentAt desc
        """)
    List<PrivateMessageEntity> findAllInvolving(@Param("me") UUID me);

    long countBySenderIdAndRecipientIdAndReadAtIsNull(UUID senderId, UUID recipientId);

    @Modifying(clearAutomatically = true)
    @Query("""
        update PrivateMessageEntity m set m.readAt = :now
        where m.recipientId = :me and m.senderId = :other and m.readAt is null
        """)
    int markAsRead(@Param("me") UUID me, @Param("other") UUID other, @Param("now") Instant now);

    /** Total des messages privés non lus reçus par un utilisateur (somme des non-lus de ses conversations). */
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
