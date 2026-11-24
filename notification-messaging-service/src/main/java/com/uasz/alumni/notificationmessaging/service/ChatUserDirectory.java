package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.model.ChatUser;
import com.uasz.alumni.notificationmessaging.repository.ChatUserRepository;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Annuaire local des utilisateurs (nom affiché, rôle applicatif, e-mail), alimenté par les jetons :
 * chaque appel enregistre le nom de l'appelant, sans interroger user-service.
 * Un utilisateur ajouté à un groupe sans s'être encore connecté au service n'a donc pas encore de nom.
 */
@Service
public class ChatUserDirectory {

    /** Dernière identité écrite par utilisateur : évite une écriture en base à chaque requête. */
    private final Map<UUID, String> lastWritten = new ConcurrentHashMap<>();
    private final ChatUserRepository repository;
    private final Clock clock;

    public ChatUserDirectory(ChatUserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Transaction séparée : l'enregistrement tient même si la requête échoue ensuite (403, 404...),
     * sinon le cache croirait le nom écrit alors que la transaction a été annulée.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remember(AuthenticatedUser user) {
        String identity = user.name() + "|" + user.role() + "|" + user.email();
        if (Objects.equals(lastWritten.get(user.id()), identity)) {
            return;
        }
        repository.upsert(user.id(), user.name(), user.role(), user.email(), OffsetDateTime.now(clock));
        lastWritten.put(user.id(), identity);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ChatUser> find(Collection<UUID> userIds) {
        return repository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getUserId, Function.identity()));
    }
}
