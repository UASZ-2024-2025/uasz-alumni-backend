package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.ChatUser;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatUserRepository extends JpaRepository<ChatUser, UUID> {

    /**
     * Insertion ou mise à jour atomique : deux premières requêtes simultanées d'un même utilisateur
     * (le front charge le groupe et ses messages en parallèle) ne doivent pas se heurter à la clé primaire.
     * Un nom ou un e-mail absent du jeton ne remplace pas celui déjà connu.
     */
    @Modifying
    @Query(value = """
            insert into chat_users (user_id, display_name, app_role, email, updated_at)
            values (:userId, :displayName, :appRole, :email, :now)
            on conflict (user_id) do update
              set display_name = coalesce(excluded.display_name, chat_users.display_name),
                  app_role = excluded.app_role,
                  email = coalesce(excluded.email, chat_users.email),
                  updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(UUID userId, String displayName, String appRole, String email, OffsetDateTime now);
}
