package com.uasz.alumni.user.service.profile;

import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import com.uasz.alumni.user.config.AppConfig;
import com.uasz.alumni.user.spi.model.RoleName;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Crée le profil d'un compte ETUDIANT ou ALUMNI dans alumni-student-company-service une fois son
 * email confirmé (ALUM-83).
 *
 * <p>Exécuté <b>après le commit</b> (aucun profil pour une activation annulée) et <b>en asynchrone</b> :
 * la confirmation de l'email n'attend ni le service distant ni ses retries. Un échec définitif est
 * journalisé et n'annule jamais l'activation du compte (ADR-0006) : une panne d'alumni-student-company-service
 * ne bloque pas l'inscription.
 */
@Component
public class ProfileProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(ProfileProvisioningListener.class);

    private final StudentCompanyClient client;

    public ProfileProvisioningListener(StudentCompanyClient client) {
        this.client = client;
    }

    @Async(AppConfig.INTER_SERVICE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileProvisioningRequested(ProfileProvisioningRequested event) {
        Optional<ProvisionedProfileType> type = profileTypeOf(event.role());
        if (type.isEmpty()) {
            return;
        }
        ProfileProvisioningRequest request = new ProfileProvisioningRequest(
                event.userId(), type.get(), event.firstName(), event.lastName(), event.email());
        try {
            client.provisionProfile(request);
            log.info("Profil {} créé pour l'utilisateur {}", type.get(), event.userId());
        } catch (RuntimeException e) {
            // Retries épuisés ou erreur non transitoire : l'activation du compte reste acquise.
            log.error("Profil non créé pour l'utilisateur {} : {}", event.userId(), e.toString());
        }
    }

    /** Seuls les comptes ETUDIANT et ALUMNI ont un profil dans alumni-student-company-service. */
    static Optional<ProvisionedProfileType> profileTypeOf(RoleName role) {
        if (role == null) {
            return Optional.empty();
        }
        return switch (role) {
            case ETUDIANT -> Optional.of(ProvisionedProfileType.ETUDIANT);
            case ALUMNI -> Optional.of(ProvisionedProfileType.ALUMNI);
            default -> Optional.empty();
        };
    }
}
