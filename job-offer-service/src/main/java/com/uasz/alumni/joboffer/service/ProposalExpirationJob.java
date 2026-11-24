package com.uasz.alumni.joboffer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expiration automatique des propositions restées {@code PENDING} au-delà de leur date limite (ALUM-174).
 */
@Component
public class ProposalExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(ProposalExpirationJob.class);

    private final ProposalService proposals;

    public ProposalExpirationJob(ProposalService proposals) {
        this.proposals = proposals;
    }

    @Scheduled(cron = "${proposals.expiration.cron}", zone = "UTC")
    public void expireOverdue() {
        try {
            int expired = proposals.expirePendingProposals();
            if (expired > 0) {
                log.info("{} proposition(s) expirée(s) automatiquement", expired);
            }
        } catch (RuntimeException e) {
            log.error("Expiration des propositions impossible : {}", e.toString());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        expireOverdue();
    }
}
