package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.security.CurrentUserProvider;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniHomeSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Synthèse de la page d'accueil de l'Alumni connecté (ALUM-246) : compteurs calculés à chaque appel à partir des
 * services qui détiennent les données, sans table ni copie.
 *
 * <ul>
 *   <li>{@code unreadMessages} : notification-messaging-service (conversations privées + groupes). Service
 *       injoignable ou en erreur : 0 et un avertissement dans les logs, la page d'accueil reste affichable.</li>
 *   <li>{@code recommendationsGiven} : 0, aucune recommandation n'est encore enregistrée
 *       ({@code POST /alumni/recommendations} répond 501).</li>
 *   <li>{@code pendingRequests} : 0, aucune demande adressée à un Alumni n'existe encore sur la plateforme.</li>
 * </ul>
 */
@Service
public class AlumniHomeSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AlumniHomeSummaryService.class);

    static final int NO_RECOMMENDATION_SOURCE = 0;
    static final int NO_REQUEST_SOURCE = 0;

    private final MessagingClient messaging;
    private final CurrentUserProvider currentUser;

    public AlumniHomeSummaryService(MessagingClient messaging, CurrentUserProvider currentUser) {
        this.messaging = messaging;
        this.currentUser = currentUser;
    }

    /**
     * Compteurs de l'Alumni connecté. Son JWT est propagé à la messagerie, qui ne renvoie que ses propres fils :
     * aucun identifiant ne vient de la requête.
     */
    public AlumniHomeSummary summaryForCurrentUser() {
        return new AlumniHomeSummary()
            .recommendationsGiven(NO_RECOMMENDATION_SOURCE)
            .pendingRequests(NO_REQUEST_SOURCE)
            .unreadMessages(unreadMessages(currentUser.requireBearerToken()));
    }

    private int unreadMessages(String bearerToken) {
        try {
            return messaging.unreadMessages(bearerToken);
        } catch (RestClientException e) {
            log.warn("Messages non lus indisponibles (notification-messaging-service) : compteur à 0 - {}",
                     e.getMessage());
            return 0;
        }
    }
}
