package com.uasz.alumni.user.service.profile;

import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProvisionedProfileType;
import com.uasz.alumni.user.config.AppConfig;
import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
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
 * alumni-student-company-service. Un refus sur un compte est journalisé sans bloquer les suivants ; un service
 * injoignable interrompt le rattrapage (il sera rejoué au prochain démarrage).
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

    public ProfileBackfill(UserAccountRepository users, StudentCompanyClient client,
                           @Qualifier(AppConfig.INTER_SERVICE_EXECUTOR) Executor executor,
                           @Value("${student-company.backfill-on-startup:true}") boolean enabled) {
        this.users = users;
        this.client = client;
        this.executor = executor;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Rattrapage des profils désactivé (PROFILE_BACKFILL_ON_STARTUP=false)");
            return;
        }
        executor.execute(this::backfill);
    }

    /** Parcourt les comptes actifs à profil, page par page ; renvoie le nombre de comptes en échec. */
    int backfill() {
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
                        // service injoignable même après les retries : inutile d'insister compte par compte
                        log.error("Rattrapage des profils interrompu, alumni-student-company-service injoignable : {}",
                                e.toString());
                        return failed + 1;
                    } catch (RuntimeException e) {
                        failed++;
                        log.error("Rattrapage du profil {} échoué pour l'utilisateur {} : {}",
                                type, account.getId(), e.toString());
                    }
                }
            } while (page.hasNext());
        }
        log.info("Rattrapage des profils terminé : {} compte(s) vérifié(s), {} échec(s)", sent, failed);
        return failed;
    }
}
