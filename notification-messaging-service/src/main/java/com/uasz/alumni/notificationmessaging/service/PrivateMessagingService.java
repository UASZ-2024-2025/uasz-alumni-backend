package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.ChatUser;
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
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateMessagingService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final PrivateMessageRepository repository;
    private final ChatUserDirectory directory;

    public PrivateMessagingService(PrivateMessageRepository repository, ChatUserDirectory directory) {
        this.repository = repository;
        this.directory = directory;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(UUID me) {
        // Messages triés du plus récent au plus ancien : le 1er rencontré par interlocuteur = dernier message
        Map<UUID, PrivateMessageEntity> lastByOther = new LinkedHashMap<>();
        for (PrivateMessageEntity m : repository.findAllInvolving(me)) {
            UUID other = m.getSenderId().equals(me) ? m.getRecipientId() : m.getSenderId();
            lastByOther.putIfAbsent(other, m);
        }
        // Noms tirés de l'annuaire local (alimenté par les jetons) : absent tant que l'interlocuteur
        // ne s'est jamais connecté à ce service, le front affiche alors un nom par défaut.
        Map<UUID, ChatUser> users = directory.find(lastByOther.keySet());
        List<ConversationSummary> result = new ArrayList<>();
        lastByOther.forEach((other, last) -> {
            ConversationSummary s = new ConversationSummary();
            s.setRecipientId(other);
            ChatUser u = users.get(other);
            if (u != null) {
                s.setRecipientName(u.getDisplayName());
                s.setRecipientRole(u.getAppRole());
            }
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

        Map<UUID, ChatUser> users = directory.find(Set.of(me, other));
        PrivateMessagePagedResponse response = new PrivateMessagePagedResponse();
        response.setContent(result.getContent().stream().map(m -> toDto(m, users)).toList());
        response.setTotalElements((int) result.getTotalElements());
        response.setPage(p);
        response.setSize(s);
        return response;
    }

    @Transactional
    public PrivateMessage send(UUID me, UUID recipientId, String content) {
        if (me.equals(recipientId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_MESSAGE_SELF", "Impossible de s'écrire à soi-même.");
        }
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE", "Le message est vide.");
        }
        if (content.length() > 5000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG", "Message trop long (5000 caractères au maximum).");
        }
        PrivateMessageEntity saved = repository.save(new PrivateMessageEntity(me, recipientId, content.trim()));
        // TODO (AL-45) : déclencher la notification MESSAGE_RECEIVED pour recipientId
        return toDto(saved, directory.find(Set.of(me, recipientId)));
    }

    @Transactional
    public void markAsRead(UUID me, UUID other) {
        repository.markAsRead(me, other, Instant.now());
    }

    /**
     * Seul l'expéditeur supprime son message. Pour tout autre appelant, la réponse est 404, comme pour un
     * message inexistant : le contrat ne prévoit pas de 403, et on ne révèle pas l'existence du message.
     */
    @Transactional
    public void delete(UUID me, UUID messageId) {
        PrivateMessageEntity m = repository.findById(messageId)
                .filter(found -> found.getSenderId().equals(me))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message introuvable."));
        repository.delete(m);
    }

    private PrivateMessage toDto(PrivateMessageEntity m, Map<UUID, ChatUser> users) {
        PrivateMessage dto = new PrivateMessage();
        dto.setId(m.getId());
        dto.setSenderId(m.getSenderId());
        dto.setRecipientId(m.getRecipientId());
        dto.setSenderName(nameOf(users, m.getSenderId()));
        dto.setRecipientName(nameOf(users, m.getRecipientId()));
        dto.setContent(m.getContent());
        dto.setIsRead(m.getReadAt() != null);
        dto.setCreatedAt(toOffset(m.getSentAt()));
        return dto;
    }

    private static String nameOf(Map<UUID, ChatUser> users, UUID id) {
        ChatUser u = users.get(id);
        return u == null ? null : u.getDisplayName();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
