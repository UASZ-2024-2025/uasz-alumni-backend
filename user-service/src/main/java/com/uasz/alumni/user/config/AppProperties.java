package com.uasz.alumni.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres applicatifs (voir {@code application.yaml}, valeurs fournies par .env / ConfigMap).
 *
 * @param frontendUrl       URL publique du frontend, base des liens envoyés par email
 * @param mailFrom          adresse d'expédition des emails
 * @param confirmationTtl   durée de validité d'un lien de confirmation (ex. 24h)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendUrl, String mailFrom, Duration confirmationTtl) {
}
