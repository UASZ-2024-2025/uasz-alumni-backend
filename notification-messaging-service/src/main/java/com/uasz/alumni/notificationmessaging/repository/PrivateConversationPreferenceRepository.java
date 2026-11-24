package com.uasz.alumni.notificationmessaging.repository;

import com.uasz.alumni.notificationmessaging.model.PrivateConversationPreference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivateConversationPreferenceRepository extends JpaRepository<PrivateConversationPreference, UUID> {

    Optional<PrivateConversationPreference> findByUserIdAndOtherId(UUID userId, UUID otherId);

    List<PrivateConversationPreference> findByUserId(UUID userId);
}
