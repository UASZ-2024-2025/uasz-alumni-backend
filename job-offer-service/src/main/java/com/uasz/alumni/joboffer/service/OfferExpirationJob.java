package com.uasz.alumni.joboffer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Une offre passe à CLOSED à sa date limite (ALUM-148) : tâche nocturne, et rattrapage au démarrage si le service
 * était arrêté. Idempotente : plusieurs réplicas peuvent l'exécuter en même temps sans conflit.
 */
@Component
public class OfferExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(OfferExpirationJob.class);

    private final OfferService offers;

    public OfferExpirationJob(OfferService offers) {
        this.offers = offers;
    }

    @Scheduled(cron = "${offers.expiration.cron}", zone = "UTC")
    public void closeExpired() {
        try {
            int closed = offers.closeExpiredOffers();
            if (closed > 0) {
                log.info("{} offre(s) fermée(s) : date limite dépassée", closed);
            }
        } catch (RuntimeException e) {
            log.error("Fermeture des offres expirées impossible : {}", e.toString());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        closeExpired();
    }
}
