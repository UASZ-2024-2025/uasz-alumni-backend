package com.uasz.alumni.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres d'authentification (voir application.yaml, valeurs fournies par .env / ConfigMap). */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Duration refreshTtl,
                             Duration passwordResetTtl,
                             int maxFailedAttempts,
                             Duration lockDuration,
                             BootstrapAdmin bootstrapAdmin) {

    public record BootstrapAdmin(String email, String password, String firstName, String lastName) {
    }
}
