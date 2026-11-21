package com.uasz.alumni.user.service.profile;

import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import com.uasz.alumni.user.config.AppConfig;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Rattrapage des profils (ALUM-214) : au démarrage, renvoie {@code POST /internal/profiles} pour chaque compte
 * ETUDIANT ou ALUMNI actif.
 *
 * <p>Le profil n'est créé qu'à la confirmation de l'email ({@link ProfileProvisioningListener}). Les comptes
 * étudiants confirmés quand alumni-student-company-service répondait encore 501, ou dont l'appel a échoué
 * (service indisponible, retries épuisés), n'ont donc aucun profil : « Profil introuvable » côté front.
 *
 * <p>Sans risque à chaque démarrage : l'opération distante est idempotente sur {@code userId} (200 si le profil
 * existe déjà). Exécuté en tâche de fond sur l'exécuteur inter-services : le démarrage n'attend pas
 * alumni-student-company-service. Un refus sur un compte est journalisé sans bloquer les suivants. Un service
 * injoignable (cas du redéploiement, où les deux services démarrent ensemble) interrompt le passage, qui est
 * retenté après {@code PROFILE_BACKFILL_RETRY_DELAY}, jusqu'à {@code PROFILE_BACKFILL_MAX_ATTEMPTS} fois.
 * Désactivable par {@code PROFILE_BACKFILL_ON_STARTUP=false}.
 */
@Component
public class ProfileBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfileBackfill.class);
    static final int PAGE_SIZE = 100;
    static final List<RoleName> PROFILE_ROLES = List.of(RoleName.ETUDIANT, RoleName.ALUMNI);

    private final UserAccountRepository users;
    private final StudentCompanyClient client;
    private final Executor executor;
    private final boolean enabled;
    private final int maxAttempts;
    private final Duration retryDelay;

    public ProfileBackfill(UserAccountRepository users, StudentCompanyClient client,
                           @Qualifier(AppConfig.INTER_SERVICE_EXECUTOR) Executor executor,
                           @Value("${student-company.backfill.on-startup:true}") boolean enabled,
                           @Value("${student-company.backfill.max-attempts:3}") int maxAttempts,
                           @Value("${student-company.backfill.retry-delay:30s}") Duration retryDelay) {
        this.users = users;
        this.client = client;
        this.executor = executor;
        this.enabled = enabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelay = retryDelay;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Rattrapage des profils désactivé (PROFILE_BACKFILL_ON_STARTUP=false)");
            return;
        }
        executor.execute(this::backfill);
    }

    /**
     * Rattrapage complet : un passage, retenté tant que le service est injoignable (au plus {@code maxAttempts}).
     * Rejouer un passage depuis le début est sans risque, l'opération distante étant idempotente.
     *
     * @return {@code true} si un passage est allé jusqu'au bout (des refus individuels restent possibles)
     */
    boolean backfill() {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                singlePass();
                return true;
            } catch (ResourceAccessException e) {
                if (attempt == maxAttempts) {
                    log.error("Rattrapage des profils abandonné après {} essai(s), alumni-student-company-service "
                            + "injoignable : {}", maxAttempts, e.toString());
                    return false;
                }
                log.warn("Rattrapage des profils : alumni-student-company-service injoignable (essai {}/{}), "
                        + "nouvel essai dans {} s", attempt, maxAttempts, retryDelay.toSeconds());
                if (!pause()) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean pause() {
        try {
            Thread.sleep(retryDelay);
            return true;
        } catch (InterruptedException e) {
            // arrêt de l'application pendant l'attente
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Un passage sur les comptes actifs à profil, page par page ; renvoie le nombre de refus.
     *
     * @throws ResourceAccessException service injoignable même après les retries du client : le passage s'arrête
     */
    int singlePass() {
        int sent = 0;
        int failed = 0;
        for (RoleName role : PROFILE_ROLES) {
            ProvisionedProfileType type = ProfileProvisioningListener.profileTypeOf(role).orElseThrow();
            Page<UserAccount> page;
            int number = 0;
            do {
                page = users.findByRoleAndStatus(role, UserStatus.ACTIF,
                        PageRequest.of(number++, PAGE_SIZE, Sort.by("id")));
                for (UserAccount account : page) {
                    try {
                        client.provisionProfile(new ProfileProvisioningRequest(account.getId(), type,
                                account.getFirstName(), account.getLastName(), account.getEmail()));
                        sent++;
                    } catch (ResourceAccessException e) {
                        throw e; // service injoignable : inutile d'insister compte par compte
                    } catch (RuntimeException e) {
                        failed++;
                        log.error("Rattrapage du profil {} échoué pour l'utilisateur {} : {}",
                                type, account.getId(), e.toString());
                    }
                }
            } while (page.hasNext());
        }
        log.info("Rattrapage des profils terminé : {} envoyé(s), {} échec(s)", sent, failed);
        return failed;
    }
}
