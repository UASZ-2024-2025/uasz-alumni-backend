package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.model.ChatGroup;
import com.uasz.alumni.notificationmessaging.model.ChatGroupMember;
import com.uasz.alumni.notificationmessaging.model.ChatGroupMessage;
import com.uasz.alumni.notificationmessaging.model.ChatUser;
import com.uasz.alumni.notificationmessaging.repository.ChatGroupMemberRepository;
import com.uasz.alumni.notificationmessaging.repository.ChatGroupMessageRepository;
import com.uasz.alumni.notificationmessaging.repository.ChatGroupRepository;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.spi.model.Group;
import com.uasz.alumni.notificationmessaging.spi.model.GroupCreateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMemberRole;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMemberRoleUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessage;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessagePagedResponse;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferencesUpdateRequest;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSettings;
import com.uasz.alumni.notificationmessaging.spi.model.GroupSummary;
import com.uasz.alumni.notificationmessaging.spi.model.GroupUpdateRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groupes de discussion (contrat notification-messaging-service 1.1.0).
 * <ul>
 *   <li>Lecture et écriture réservées aux membres (403 sinon) ; on n'entre dans un groupe qu'en y étant ajouté.</li>
 *   <li>OWNER (un seul) et ADMIN sont les administrateurs du groupe ; les réglages ouvrent ou non
 *       l'envoi, la modification des infos et l'ajout de membres aux simples MEMBER.</li>
 *   <li>Si le propriétaire part, la propriété passe à l'ADMIN le plus ancien, sinon au membre le plus ancien ;
 *       le groupe disparaît avec son dernier membre.</li>
 *   <li>Chaque événement (création, ajout, départ, rôle, réglage) laisse un message SYSTEM dans le fil.</li>
 * </ul>
 */
@Service
@Transactional
public class GroupService {

    static final int MAX_MESSAGE_LENGTH = 4000;
    static final int MAX_PAGE_SIZE = 100;
    private static final String ADMINISTRATEUR = "ADMINISTRATEUR";

    private final ChatGroupRepository groups;
    private final ChatGroupMemberRepository members;
    private final ChatGroupMessageRepository messages;
    private final ChatUserDirectory directory;
    private final Clock clock;

    public GroupService(ChatGroupRepository groups, ChatGroupMemberRepository members,
                        ChatGroupMessageRepository messages, ChatUserDirectory directory, Clock clock) {
        this.groups = groups;
        this.members = members;
        this.messages = messages;
        this.directory = directory;
        this.clock = clock;
    }

    // ───── Lecture ─────

    /** Groupes de l'appelant : épinglés d'abord, puis par activité la plus récente. */
    @Transactional(readOnly = true)
    public List<GroupSummary> listGroups(AuthenticatedUser caller) {
        OffsetDateTime now = now();
        List<ChatGroupMember> memberships = members.findByUserId(caller.id());
        Map<UUID, ChatGroup> byId = new java.util.HashMap<>();
        groups.findAllById(memberships.stream().map(ChatGroupMember::getGroupId).toList())
                .forEach(g -> byId.put(g.getId(), g));

        List<GroupSummary> summaries = new ArrayList<>();
        Map<UUID, ChatGroupMessage> lastMessages = new java.util.HashMap<>();
        for (ChatGroupMember me : memberships) {
            ChatGroup g = byId.get(me.getGroupId());
            if (g == null) {
                continue;
            }
            Optional<ChatGroupMessage> last = messages.findFirstByGroupIdOrderByCreatedAtDescSeqDesc(g.getId());
            last.ifPresent(m -> lastMessages.put(g.getId(), m));
            summaries.add(new GroupSummary()
                    .id(g.getId())
                    .name(g.getName())
                    .memberCount((int) members.countByGroupId(g.getId()))
                    .lastMessage(last.map(ChatGroupMessage::getContent).orElse(null))
                    .lastMessageType(last.map(ChatGroupMessage::getType).orElse(null))
                    .lastMessageTime(last.map(ChatGroupMessage::getCreatedAt).orElse(g.getCreatedAt()))
                    .unreadCount((int) messages.countUnread(g.getId(), caller.id(), readSince(me)))
                    .preferences(GroupViews.preferences(me, now)));
        }

        Map<UUID, ChatUser> senders = directory.find(lastMessages.values().stream().map(ChatGroupMessage::getSenderId).toList());
        summaries.forEach(s -> Optional.ofNullable(lastMessages.get(s.getId()))
                .map(m -> senders.get(m.getSenderId()))
                .ifPresent(u -> s.setLastMessageSenderName(u.getDisplayName())));

        summaries.sort(Comparator
                .comparing((GroupSummary s) -> !Boolean.TRUE.equals(s.getPreferences().getPinned()))
                .thenComparing(GroupSummary::getLastMessageTime, Comparator.reverseOrder()));
        return summaries;
    }

    @Transactional(readOnly = true)
    public Group getGroup(UUID groupId, AuthenticatedUser caller) {
        ChatGroup g = requireGroup(groupId);
        return view(g, requireMember(groupId, caller));
    }

    /** Page 0 = messages les plus récents ; chaque page est rendue dans l'ordre chronologique. */
    @Transactional(readOnly = true)
    public GroupMessagePagedResponse getMessages(UUID groupId, Integer page, Integer size, AuthenticatedUser caller) {
        requireGroup(groupId);
        requireMember(groupId, caller);
        int p = page != null ? page : 0;
        int s = size != null ? size : 30;
        if (p < 0 || s < 1 || s > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "page doit être positif et size compris entre 1 et " + MAX_PAGE_SIZE);
        }
        Page<ChatGroupMessage> result = messages.findByGroupIdOrderByCreatedAtDescSeqDesc(groupId, PageRequest.of(p, s));
        List<ChatGroupMessage> chronological = new ArrayList<>(result.getContent());
        java.util.Collections.reverse(chronological);
        Map<UUID, ChatUser> senders = directory.find(chronological.stream().map(ChatGroupMessage::getSenderId).toList());
        return new GroupMessagePagedResponse()
                .content(chronological.stream().map(m -> GroupViews.message(m, senders.get(m.getSenderId()))).toList())
                .totalElements((int) result.getTotalElements())
                .page(p)
                .size(s);
    }

    // ───── Création, infos, réglages ─────

    public Group createGroup(GroupCreateRequest request, AuthenticatedUser caller) {
        OffsetDateTime now = now();
        String name = validName(request.getName());
        ChatGroup g = groups.save(new ChatGroup(name, blankToNull(request.getDescription()), caller.id(), now));

        ChatGroupMember owner = new ChatGroupMember(g.getId(), caller.id(), GroupMemberRole.OWNER, now);
        owner.markRead(now);
        List<ChatGroupMember> created = new ArrayList<>(List.of(owner));
        distinctIds(request.getMemberIds()).stream()
                .filter(id -> !id.equals(caller.id()))
                .forEach(id -> created.add(new ChatGroupMember(g.getId(), id, GroupMemberRole.MEMBER, now)));
        members.saveAll(created);

        system(g, caller.id(), actor(caller) + " a créé le groupe « " + name + " »", now);
        return view(g, owner);
    }

    public Group updateGroup(UUID groupId, GroupUpdateRequest request, AuthenticatedUser caller) {
        OffsetDateTime now = now();
        ChatGroup g = requireGroupForUpdate(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        if (g.isOnlyAdminsCanEditInfo() && !me.isGroupAdmin()) {
            throw forbidden("Seuls les administrateurs du groupe peuvent modifier ses informations.");
        }
        if (request.getName() != null) {
            String name = validName(request.getName());
            if (!name.equals(g.getName())) {
                g.rename(name);
                system(g, caller.id(), actor(caller) + " a renommé le groupe en « " + name + " »", now);
            }
        }
        if (request.getDescription() != null) {
            String description = blankToNull(request.getDescription());
            if (!Objects.equals(description, g.getDescription())) {
                g.describe(description);
                system(g, caller.id(), actor(caller) + " a modifié la description du groupe", now);
            }
        }
        return view(g, me);
    }

    public Group updateSettings(UUID groupId, GroupSettings settings, AuthenticatedUser caller) {
        OffsetDateTime now = now();
        ChatGroup g = requireGroupForUpdate(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        if (!me.isGroupAdmin()) {
            throw forbidden("Seuls les administrateurs du groupe peuvent modifier ses réglages.");
        }
        String who = actor(caller);
        if (settings.getOnlyAdminsCanSend() != null && settings.getOnlyAdminsCanSend() != g.isOnlyAdminsCanSend()) {
            g.setOnlyAdminsCanSend(settings.getOnlyAdminsCanSend());
            system(g, caller.id(), who + " a modifié les réglages : "
                    + scope(settings.getOnlyAdminsCanSend()) + " peuvent envoyer des messages", now);
        }
        if (settings.getOnlyAdminsCanEditInfo() != null && settings.getOnlyAdminsCanEditInfo() != g.isOnlyAdminsCanEditInfo()) {
            g.setOnlyAdminsCanEditInfo(settings.getOnlyAdminsCanEditInfo());
            system(g, caller.id(), who + " a modifié les réglages : "
                    + scope(settings.getOnlyAdminsCanEditInfo()) + " peuvent modifier les infos du groupe", now);
        }
        if (settings.getOnlyAdminsCanAddMembers() != null
                && settings.getOnlyAdminsCanAddMembers() != g.isOnlyAdminsCanAddMembers()) {
            g.setOnlyAdminsCanAddMembers(settings.getOnlyAdminsCanAddMembers());
            system(g, caller.id(), who + " a modifié les réglages : "
                    + scope(settings.getOnlyAdminsCanAddMembers()) + " peuvent ajouter des participants", now);
        }
        return view(g, me);
    }

    public GroupPreferences updatePreferences(UUID groupId, GroupPreferencesUpdateRequest request,
                                              AuthenticatedUser caller) {
        OffsetDateTime now = now();
        requireGroup(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        Preferences.apply(me, request, now);
        return GroupViews.preferences(me, now);
    }

    /** Propriétaire, ou ADMINISTRATEUR de la plateforme. */
    public void deleteGroup(UUID groupId, AuthenticatedUser caller) {
        ChatGroup g = requireGroupForUpdate(groupId);
        boolean owner = members.findByGroupIdAndUserId(groupId, caller.id()).map(ChatGroupMember::isOwner).orElse(false);
        if (!owner && !ADMINISTRATEUR.equals(caller.role())) {
            throw forbidden("Seul le propriétaire du groupe ou un administrateur peut le supprimer.");
        }
        groups.delete(g); // membres et messages suivent (ON DELETE CASCADE)
    }

    // ───── Membres ─────

    public Group addMembers(UUID groupId, Collection<UUID> memberIds, AuthenticatedUser caller) {
        OffsetDateTime now = now();
        ChatGroup g = requireGroupForUpdate(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        if (g.isOnlyAdminsCanAddMembers() && !me.isGroupAdmin()) {
            throw forbidden("Seuls les administrateurs du groupe peuvent ajouter des participants.");
        }
        Set<UUID> present = new HashSet<>();
        members.findByGroupIdOrderByJoinedAtAsc(groupId).forEach(m -> present.add(m.getUserId()));
        List<UUID> added = distinctIds(memberIds).stream().filter(id -> !present.contains(id)).toList();
        if (!added.isEmpty()) {
            members.saveAll(added.stream().map(id -> new ChatGroupMember(groupId, id, GroupMemberRole.MEMBER, now)).toList());
            system(g, caller.id(), actor(caller) + " a ajouté " + people(added), now);
        }
        return view(g, me);
    }

    public Group updateMemberRole(UUID groupId, UUID userId, GroupMemberRoleUpdateRequest request,
                                  AuthenticatedUser caller) {
        OffsetDateTime now = now();
        ChatGroup g = requireGroupForUpdate(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        if (!me.isOwner()) {
            throw forbidden("Seul le propriétaire du groupe peut nommer ou retirer des administrateurs.");
        }
        ChatGroupMember target = requireTarget(groupId, userId);
        if (target.isOwner()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Le rôle du propriétaire ne peut pas être modifié.");
        }
        GroupMemberRole role = GroupMemberRole.fromValue(request.getGroupRole().getValue());
        if (role != target.getGroupRole()) {
            target.changeRole(role);
            String who = people(List.of(userId));
            system(g, caller.id(), role == GroupMemberRole.ADMIN
                    ? actor(caller) + " a nommé " + who + " administrateur"
                    : actor(caller) + " a retiré " + who + " des administrateurs", now);
        }
        return view(g, me);
    }

    /**
     * Quitter (userId = appelant) ou retirer un membre : le propriétaire retire n'importe qui,
     * un ADMIN retire les ADMIN et MEMBER, jamais le propriétaire.
     */
    public void removeMember(UUID groupId, UUID userId, AuthenticatedUser caller) {
        OffsetDateTime now = now();
        ChatGroup g = requireGroupForUpdate(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        ChatGroupMember target = requireTarget(groupId, userId);
        boolean leaving = userId.equals(caller.id());
        if (!leaving && !(me.isOwner() || (me.getGroupRole() == GroupMemberRole.ADMIN && !target.isOwner()))) {
            throw forbidden("Vous ne pouvez pas retirer ce membre du groupe.");
        }

        members.delete(target);
        members.flush(); // libère l'index « un seul propriétaire » avant une éventuelle passation
        List<ChatGroupMember> remaining = members.findByGroupIdOrderByJoinedAtAsc(groupId);
        if (remaining.isEmpty()) {
            groups.delete(g);
            return;
        }
        system(g, caller.id(), leaving
                ? actor(caller) + " a quitté le groupe"
                : actor(caller) + " a retiré " + people(List.of(userId)), now);

        if (target.isOwner()) {
            ChatGroupMember successor = remaining.stream()
                    .filter(m -> m.getGroupRole() == GroupMemberRole.ADMIN).findFirst()
                    .orElse(remaining.getFirst());
            successor.changeRole(GroupMemberRole.OWNER);
            g.transferOwnership(successor.getUserId());
            system(g, successor.getUserId(), capitalize(people(List.of(successor.getUserId())))
                    + " est maintenant propriétaire du groupe", now);
        }
    }

    // ───── Messages ─────

    public GroupMessage sendMessage(UUID groupId, String content, AuthenticatedUser caller) {
        OffsetDateTime now = now();
        ChatGroup g = requireGroup(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        if (g.isOnlyAdminsCanSend() && !me.isGroupAdmin()) {
            throw forbidden("Seuls les administrateurs peuvent envoyer des messages dans ce groupe.");
        }
        String text = validText(content);
        ChatGroupMessage saved = messages.save(new ChatGroupMessage(groupId, caller.id(), GroupMessageType.TEXT, text, now));
        g.touch(now);
        me.markRead(now); // son propre message n'est pas un non-lu
        return GroupViews.message(saved, directory.find(List.of(caller.id())).get(caller.id()));
    }

    /**
     * Seul l'auteur modifie son message, dans le délai de {@link MessageEdits#WINDOW} et tant qu'il peut écrire
     * dans le groupe (réglage « seuls les administrateurs envoient »).
     */
    public GroupMessage editMessage(UUID groupId, UUID messageId, String content, AuthenticatedUser caller) {
        OffsetDateTime now = now();
        ChatGroup g = requireGroup(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        ChatGroupMessage message = messages.findByIdAndGroupId(messageId, groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message introuvable."));
        if (message.getType() == GroupMessageType.SYSTEM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "Les messages système ne peuvent pas être modifiés.");
        }
        if (!message.getSenderId().equals(caller.id())) {
            throw forbidden("Vous ne pouvez modifier que vos propres messages.");
        }
        if (g.isOnlyAdminsCanSend() && !me.isGroupAdmin()) {
            throw forbidden("Seuls les administrateurs peuvent écrire dans ce groupe.");
        }
        MessageEdits.requireEditable(message.isDeleted(), message.getCreatedAt(), now);
        String text = validText(content);
        if (!text.equals(message.getContent())) {
            message.edit(text, now);
        }
        return GroupViews.message(message, directory.find(List.of(caller.id())).get(caller.id()));
    }

    /** L'auteur supprime son message ; OWNER et ADMIN suppriment celui de n'importe quel membre. */
    public void deleteMessage(UUID groupId, UUID messageId, AuthenticatedUser caller) {
        requireGroup(groupId);
        ChatGroupMember me = requireMember(groupId, caller);
        ChatGroupMessage message = messages.findByIdAndGroupId(messageId, groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message introuvable."));
        if (message.getType() == GroupMessageType.SYSTEM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "Les messages système ne peuvent pas être supprimés.");
        }
        if (message.isDeleted()) {
            return;
        }
        if (!message.getSenderId().equals(caller.id()) && !me.isGroupAdmin()) {
            throw forbidden("Vous ne pouvez supprimer que vos propres messages.");
        }
        message.delete();
    }

    public void markAsRead(UUID groupId, AuthenticatedUser caller) {
        requireGroup(groupId);
        requireMember(groupId, caller).markRead(now());
    }

    // ───── Outils ─────

    private Group view(ChatGroup g, ChatGroupMember me) {
        List<ChatGroupMember> all = members.findByGroupIdOrderByJoinedAtAsc(g.getId());
        Map<UUID, ChatUser> users = directory.find(all.stream().map(ChatGroupMember::getUserId).toList());
        return GroupViews.group(g, all, me, users, now());
    }

    private ChatGroup requireGroup(UUID groupId) {
        return groups.findById(groupId).orElseThrow(() -> notFound(groupId));
    }

    private ChatGroup requireGroupForUpdate(UUID groupId) {
        return groups.findByIdForUpdate(groupId).orElseThrow(() -> notFound(groupId));
    }

    private ChatGroupMember requireMember(UUID groupId, AuthenticatedUser caller) {
        return members.findByGroupIdAndUserId(groupId, caller.id())
                .orElseThrow(() -> forbidden("Vous n'êtes pas membre de ce groupe."));
    }

    private ChatGroupMember requireTarget(UUID groupId, UUID userId) {
        return members.findByGroupIdAndUserId(groupId, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Cette personne ne fait pas partie du groupe."));
    }

    private void system(ChatGroup g, UUID actorId, String text, OffsetDateTime now) {
        messages.save(new ChatGroupMessage(g.getId(), actorId, GroupMessageType.SYSTEM, text, now));
        g.touch(now);
    }

    /** Messages non lus comptés depuis la dernière lecture, jamais avant l'arrivée dans le groupe. */
    private static OffsetDateTime readSince(ChatGroupMember me) {
        OffsetDateTime read = me.getLastReadAt();
        return read != null && read.isAfter(me.getJoinedAt()) ? read : me.getJoinedAt();
    }

    private static String actor(AuthenticatedUser caller) {
        return caller.name() != null && !caller.name().isBlank() ? caller.name() : "Un participant";
    }

    /** « Awa Diop, Moussa Ndiaye et 2 autres participants » (les inconnus de l'annuaire sont comptés). */
    private String people(List<UUID> ids) {
        Map<UUID, ChatUser> known = directory.find(ids);
        List<String> names = ids.stream().map(known::get).filter(Objects::nonNull)
                .map(ChatUser::getDisplayName).filter(n -> n != null && !n.isBlank()).toList();
        int unknown = ids.size() - names.size();
        List<String> parts = new ArrayList<>(names);
        if (unknown > 0) {
            parts.add(unknown == 1 ? (names.isEmpty() ? "un participant" : "1 autre participant")
                    : unknown + (names.isEmpty() ? " participants" : " autres participants"));
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " et " + parts.getLast();
    }

    private static String scope(boolean adminsOnly) {
        return adminsOnly ? "seuls les administrateurs" : "tous les participants";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.length() < 3 || name.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GROUP",
                    "Le nom du groupe doit contenir entre 3 et 100 caractères.");
        }
        return name;
    }

    private static String validText(String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "Le message ne peut pas être vide.");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE",
                    "Le message ne peut pas dépasser " + MAX_MESSAGE_LENGTH + " caractères.");
        }
        return text;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static List<UUID> distinctIds(Collection<UUID> ids) {
        return ids == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private OffsetDateTime now() {
        // Précision de PostgreSQL : évite qu'un horodatage relu diffère de celui écrit.
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static ApiException notFound(UUID groupId) {
        return new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Groupe introuvable : " + groupId);
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
