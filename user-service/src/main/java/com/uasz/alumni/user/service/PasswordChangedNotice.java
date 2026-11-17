package com.uasz.alumni.user.service;

/** Événement : notifier l'utilisateur que son mot de passe vient de changer (alerte en cas de piratage). */
public record PasswordChangedNotice(String email, String firstName) {
}
