package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.config.AsyncResilienceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Rattache le compte ENTREPRISE à la fiche qu'il vient de créer (ALUM-168).
 *
 * <p>Exécuté <b>après le commit</b> (aucun rattachement pour une création annulée) et <b>en asynchrone</b> : la
 * création de la fiche n'attend ni user-service ni ses retries. Un échec définitif est journalisé et n'annule
 * jamais la fiche (ADR-0006) : un administrateur peut rattraper avec {@code PUT /companies/{id}/owner}.
 */
@Component
public class CompanyAccountLinkListener {

    private static final Logger log = LoggerFactory.getLogger(CompanyAccountLinkListener.class);

    private final UserServiceClient client;

    public CompanyAccountLinkListener(UserServiceClient client) {
        this.client = client;
    }

    @Async(AsyncResilienceConfig.INTER_SERVICE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLinkRequested(CompanyAccountLinkRequested event) {
        try {
            client.linkCompany(event.userId(), event.companyId());
            log.info("Compte {} rattaché à la fiche {}", event.userId(), event.companyId());
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Rattachement refusé (409) pour le compte {} et la fiche {} : {}",
                    event.userId(), event.companyId(), e.getStatusText());
        } catch (RuntimeException e) {
            // Retries épuisés ou erreur non transitoire : la fiche reste créée.
            log.error("Compte {} non rattaché à la fiche {} : {} (à rattraper par PUT /companies/{}/owner)",
                    event.userId(), event.companyId(), e.toString(), event.companyId());
        }
    }
}
