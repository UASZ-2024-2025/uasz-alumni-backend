package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.ChatUser;
import com.uasz.alumni.notificationmessaging.model.PrivateConversationPreference;
import com.uasz.alumni.notificationmessaging.model.PrivateMessageEntity;
import com.uasz.alumni.notificationmessaging.repository.PrivateConversationPreferenceRepository;
import com.uasz.alumni.notificationmessaging.repository.PrivateMessageRepository;
import com.uasz.alumni.notificationmessaging.spi.model.ConversationSummary;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferencesUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessage;
import com.uasz.alumni.notificationmessaging.spi.model.PrivateMessagePagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.SendMessageRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    static final int MAX_MESSAGE_LENGTH = 5000;

    private final PrivateMessageRepository repository;
    private final PrivateConversationPreferenceRepository preferences;
    private final ChatUserDirectory directory;
    private final MessageAttachments attachments;
    private final Clock clock;

    public PrivateMessagingService(PrivateMessageRepository repository,
                                   PrivateConversationPreferenceRepository preferences,
                                   ChatUserDirectory directory, MessageAttachments attachments, Clock clock) {
        this.repository = repository;
        this.preferences = preferences;
        this.directory = directory;
        this.attachments = attachments;
        this.clock = clock;
    }

    /** Conversations de l'appelant : épinglées d'abord, puis par dernier message le plus récent. */
    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(UUID me) {
        OffsetDateTime now = now();
        // Messages triés du plus récent au plus ancien : le 1er rencontré par interlocuteur = dernier message
        Map<UUID, PrivateMessageEntity> lastByOther = new LinkedHashMap<>();
        for (PrivateMessageEntity m : repository.findAllInvolving(me)) {
            UUID other = m.getSenderId().equals(me) ? m.getRecipientId() : m.getSenderId();
            lastByOther.putIfAbsent(other, m);
        }
        Map<UUID, PrivateConversationPreference> prefs = preferences.findByUserId(me).stream()
                .collect(Collectors.toMap(PrivateConversationPreference::getOtherId, Function.identity()));
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
            s.setLastMessageSenderId(last.getSenderId());
            s.setLastMessageDeleted(last.isDeleted());
            s.setLastMessageType(last.getType());
            s.setLastMessageAttachmentKind(last.getAttachment() == null ? null : last.getAttachment().getKind());
            s.setLastMessageTime(toOffset(last.getSentAt()));
            s.setUnreadCount((int) repository
                    .countBySenderIdAndRecipientIdAndReadAtIsNull(other, me));
            s.setPreferences(Preferences.view(prefs.get(other), now));
            result.add(s);
        });
        // Tri stable : à épinglage égal, l'ordre du plus récent au plus ancien est conservé.
        result.sort(Comparator.comparing((ConversationSummary s) -> !Boolean.TRUE.equals(s.getPreferences().getPinned())));
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

    /** Message texte. */
    @Transactional
    public PrivateMessage send(UUID me, UUID recipientId, String content) {
        return send(me, recipientId, new SendMessageRequest().content(content));
    }

    /** Texte, sticker ou pièce jointe (ALUM-261, voir {@link MessageAttachments#compose}). */
    @Transactional
    public PrivateMessage send(UUID me, UUID recipientId, SendMessageRequest request) {
        requireOther(me, recipientId);
        MessageAttachments.Composed c = attachments.compose(request, me, PrivateMessagingService::validText);
        PrivateMessageEntity saved = repository.save(new PrivateMessageEntity(
                me, recipientId, c.type(), c.content(), c.sticker(), c.attachment(), now().toInstant()));
        // TODO (AL-45) : déclencher la notification MESSAGE_RECEIVED pour recipientId
        return toDto(saved, directory.find(Set.of(me, recipientId)));
    }

    @Transactional
    public void markAsRead(UUID me, UUID other) {
        repository.markAsRead(me, other, now().toInstant());
    }

    /**
     * Seul l'expéditeur modifie son message, dans le délai de {@link MessageEdits#WINDOW}. Pour tout autre
     * appelant, 404 comme pour un message inexistant (voir {@link #delete}).
     */
    @Transactional
    public PrivateMessage edit(UUID me, UUID messageId, String content) {
        PrivateMessageEntity m = requireOwnMessage(me, messageId);
        attachments.requireEditable(m.getType(), m.getAttachment());
        Instant now = now().toInstant();
        MessageEdits.requireEditable(m.isDeleted(), toOffset(m.getSentAt()), toOffset(now));
        String text = attachments.editedContent(m.getType(), content, PrivateMessagingService::validText);
        if (!Objects.equals(text, m.getContent())) {
            m.edit(text, now);
        }
        return toDto(m, directory.find(Set.of(m.getSenderId(), m.getRecipientId())));
    }

    /**
     * Seul l'expéditeur supprime son message, pour les deux interlocuteurs : il reste dans le fil, sans contenu.
     * Pour tout autre appelant, la réponse est 404, comme pour un message inexistant : le contrat ne prévoit pas
     * de 403, et on ne révèle pas l'existence du message.
     */
    @Transactional
    public void delete(UUID me, UUID messageId) {
        attachments.discard(requireOwnMessage(me, messageId).delete());
    }

    /** Préférences personnelles de l'appelant sur sa conversation avec {@code other}. */
    @Transactional
    public GroupPreferences updatePreferences(UUID me, UUID other, GroupPreferencesUpdateRequest request) {
        requireOther(me, other);
        OffsetDateTime now = now();
        PrivateConversationPreference p = preferences.findByUserIdAndOtherId(me, other)
                .orElseGet(() -> preferences.save(new PrivateConversationPreference(me, other)));
        Preferences.apply(p, request, now);
        return Preferences.view(p, now);
    }

    private PrivateMessageEntity requireOwnMessage(UUID me, UUID messageId) {
        return repository.findById(messageId)
                .filter(found -> found.getSenderId().equals(me))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message introuvable."));
    }

    private static void requireOther(UUID me, UUID other) {
        if (me.equals(other)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_MESSAGE_SELF", "Impossible de s'écrire à soi-même.");
        }
    }

    private static String validText(String content) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE", "Le message est vide.");
        }
        String text = content.trim();
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG",
                    "Message trop long (" + MAX_MESSAGE_LENGTH + " caractères au maximum).");
        }
        return text;
    }

    private PrivateMessage toDto(PrivateMessageEntity m, Map<UUID, ChatUser> users) {
        PrivateMessage dto = new PrivateMessage();
        dto.setId(m.getId());
        dto.setSenderId(m.getSenderId());
        dto.setRecipientId(m.getRecipientId());
        dto.setSenderName(nameOf(users, m.getSenderId()));
        dto.setRecipientName(nameOf(users, m.getRecipientId()));
        dto.setType(m.getType());
        dto.setContent(m.getContent());
        dto.setSticker(m.getSticker());
        dto.setAttachment(attachments.view(m.getAttachment()));
        dto.setIsRead(m.getReadAt() != null);
        dto.setDeleted(m.isDeleted());
        dto.setCreatedAt(toOffset(m.getSentAt()));
        dto.setEditedAt(toOffset(m.getEditedAt()));
        return dto;
    }

    private static String nameOf(Map<UUID, ChatUser> users, UUID id) {
        ChatUser u = users.get(id);
        return u == null ? null : u.getDisplayName();
    }

    private OffsetDateTime now() {
        // Précision de PostgreSQL : évite qu'un horodatage relu diffère de celui écrit.
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
