package com.uasz.alumni.notificationmessaging.model;

import java.time.OffsetDateTime;

/** Préférences personnelles sur un groupe ou une conversation privée : épinglé, archivé, sourdine. */
public interface PersonalPreferences {

    boolean isPinned();

    boolean isArchived();

    OffsetDateTime getMutedUntil();

    boolean isMutedForever();

    void setPinned(boolean pinned);

    void setArchived(boolean archived);

    void mute(OffsetDateTime until, boolean forever);

    default boolean isMuted(OffsetDateTime now) {
        return isMutedForever() || (getMutedUntil() != null && getMutedUntil().isAfter(now));
    }
}
