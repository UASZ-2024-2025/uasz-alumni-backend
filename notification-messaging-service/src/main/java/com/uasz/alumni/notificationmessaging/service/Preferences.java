package com.uasz.alumni.notificationmessaging.service;

import com.uasz.alumni.notificationmessaging.model.PersonalPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferencesUpdateRequest;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Préférences personnelles, communes aux groupes et aux conversations privées. */
final class Preferences {

    private Preferences() {
    }

    /** Seuls les champs fournis sont modifiés. */
    static void apply(PersonalPreferences p, GroupPreferencesUpdateRequest request, OffsetDateTime now) {
        if (request.getPinned() != null) {
            p.setPinned(request.getPinned());
        }
        if (request.getArchived() != null) {
            p.setArchived(request.getArchived());
        }
        if (request.getMute() != null) {
            switch (request.getMute()) {
                case NONE -> p.mute(null, false);
                case EIGHT_HOURS -> p.mute(now.plus(Duration.ofHours(8)), false);
                case ONE_WEEK -> p.mute(now.plus(Duration.ofDays(7)), false);
                case ALWAYS -> p.mute(null, true);
            }
        }
    }

    /** {@code null} : aucune préférence enregistrée, les valeurs par défaut s'appliquent. */
    static GroupPreferences view(PersonalPreferences p, OffsetDateTime now) {
        if (p == null) {
            return new GroupPreferences().pinned(false).archived(false).muted(false);
        }
        boolean muted = p.isMuted(now);
        return new GroupPreferences()
                .pinned(p.isPinned())
                .archived(p.isArchived())
                .muted(muted)
                .mutedUntil(muted && !p.isMutedForever() ? p.getMutedUntil() : null);
    }
}
