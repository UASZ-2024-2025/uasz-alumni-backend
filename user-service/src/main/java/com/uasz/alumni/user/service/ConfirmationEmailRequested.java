package com.uasz.alumni.user.service;

/**
 * Événement publié quand un email de confirmation doit partir. Il est traité après le commit de la
 * transaction : on n'envoie jamais un lien pour un compte qui n'a finalement pas été enregistré.
 * Le jeton en clair ne vit que dans cet événement, jamais en base.
 */
public record ConfirmationEmailRequested(String email, String firstName, String token) {

    @Override
    public String toString() {
        return "ConfirmationEmailRequested[email=" + email + ", token=***]";
    }
}
