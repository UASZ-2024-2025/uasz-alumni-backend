package com.uasz.alumni.user.service;

import java.time.Duration;

/** Événement : un email de reset (ou d'invitation) doit partir après le commit. Le jeton en clair ne vit que dans l'événement. */
public record PasswordEmailRequested(String email, String firstName, String token, Kind kind, Duration ttl) {

    public enum Kind { RESET, INVITATION }

    @Override
    public String toString() {
        return "PasswordEmailRequested[email=" + email + ", kind=" + kind + ", token=***]";
    }
}
