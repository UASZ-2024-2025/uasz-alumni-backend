package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.model.PrivateMessageEntity;
import com.uasz.alumni.notificationmessaging.repository.PrivateMessageRepository;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessage;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessagePagedResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PrivateMessagingService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final PrivateMessageRepository repository;

    public PrivateMessagingService(PrivateMessageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(UUID me) {
        // Messages triés du plus récent au plus ancien : le 1er rencontré par interlocuteur = dernier message
        Map<UUID, PrivateMessageEntity> lastByOther = new LinkedHashMap<>();
        for (PrivateMessageEntity m : repository.findAllInvolving(me)) {
            UUID other = m.getSenderId().equals(me) ? m.getRecipientId() : m.getSenderId();
            lastByOther.putIfAbsent(other, m);
        }
        List<ConversationSummary> result = new ArrayList<>();
        lastByOther.forEach((other, last) -> {
            ConversationSummary s = new ConversationSummary();
            s.setRecipientId(other);
            // TODO : recipientName / recipientRole viennent du user-service
            s.setLastMessage(last.getContent());
            s.setLastMessageTime(toOffset(last.getSentAt()));
            s.setUnreadCount((int) repository
                    .countBySenderIdAndRecipientIdAndReadAtIsNull(other, me));
            result.add(s);
        });
        return result;
    }

    @Transactional(readOnly = true)
    public PrivateMessagePagedResponse getConversation(UUID me, UUID other, Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Page<PrivateMessageEntity> result = repository.findConversation(
                me, other, PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "sentAt")));

        PrivateMessagePagedResponse response = new PrivateMessagePagedResponse();
        response.setContent(result.getContent().stream().map(this::toDto).toList());
        response.setTotalElements((int) result.getTotalElements());
        response.setPage(p);
        response.setSize(s);
        return response;
    }

    @Transactional
    public PrivateMessage send(UUID me, UUID recipientId, String content) {
        if (me.equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de s'écrire à soi-même");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message est vide");
        }
        if (content.length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message trop long (5000 max)");
        }
        PrivateMessageEntity saved = repository.save(new PrivateMessageEntity(me, recipientId, content.trim()));
        // TODO (AL-45) : déclencher la notification MESSAGE_RECEIVED pour recipientId
        return toDto(saved);
    }

    @Transactional
    public void markAsRead(UUID me, UUID other) {
        repository.markAsRead(me, other, Instant.now());
    }

    @Transactional
    public void delete(UUID me, UUID messageId) {
        PrivateMessageEntity m = repository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message introuvable"));
        if (!m.getSenderId().equals(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul l'expéditeur peut supprimer");
        }
        repository.delete(m);
    }

    private PrivateMessage toDto(PrivateMessageEntity m) {
        PrivateMessage dto = new PrivateMessage();
        dto.setId(m.getId());
        dto.setSenderId(m.getSenderId());
        dto.setRecipientId(m.getRecipientId());
        // TODO : senderName / recipientName viennent du user-service
        dto.setContent(m.getContent());
        dto.setIsRead(m.getReadAt() != null);
        dto.setCreatedAt(toOffset(m.getSentAt()));
        return dto;
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}