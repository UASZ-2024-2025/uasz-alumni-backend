package com.uasz.alumni.user.config;

import com.uasz.alumni.user.model.UserAccount;
import com.uasz.alumni.user.repository.UserAccountRepository;
import com.uasz.alumni.user.service.AuditService;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.PasswordPolicy;
import com.uasz.alumni.user.utils.TextUtils;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crée le premier ADMINISTRATEUR au démarrage s'il n'en existe aucun d'actif. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthProperties props;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;

    public AdminBootstrap(AuthProperties props, UserAccountRepository users, PasswordEncoder encoder,
                          AuditService audit, Clock clock) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin cfg = props.bootstrapAdmin();
        if (cfg == null || isBlank(cfg.email()) || isBlank(cfg.password())) {
            log.info("Bootstrap ADMINISTRATEUR ignoré (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD vides)");
            return;
        }
        if (cfg.password().startsWith("CHANGE_ME") || !PasswordPolicy.isValid(cfg.password())) {
            log.error("Bootstrap ADMINISTRATEUR ignoré : mot de passe CHANGE_ME ou non conforme "
                    + "(8 à 72 octets, une lettre et un chiffre). Lancez 'make env-complete'.");
            return;
        }
        if (isBlank(cfg.firstName()) || isBlank(cfg.lastName())) {
            log.error("Bootstrap ADMINISTRATEUR ignoré : BOOTSTRAP_ADMIN_FIRST_NAME / "
                    + "BOOTSTRAP_ADMIN_LAST_NAME sont vides.");
            return;
        }
        if (users.countByRoleAndStatus(RoleName.ADMINISTRATEUR, UserStatus.ACTIF) > 0) {
            return;
        }
        UserAccount admin = new UserAccount(
                TextUtils.normalizeEmail(cfg.email()),
                encoder.encode(cfg.password()),
                cfg.firstName().trim(),
                cfg.lastName().trim(),
                RoleName.ADMINISTRATEUR,
                UserStatus.EN_ATTENTE);
        admin.activate(clock.instant());
        try {
            users.saveAndFlush(admin);
            audit.record("ADMIN_BOOTSTRAPPED", null, admin.getId(), null);
            log.warn("Compte ADMINISTRATEUR initial créé ({}). Changez son mot de passe dès la première connexion.",
                    admin.getEmail());
        } catch (DataIntegrityViolationException e) {
            // ex. 2 réplicas qui démarrent en même temps : l'autre a gagné la course
            log.warn("Bootstrap ADMINISTRATEUR non effectué : {}", e.getMostSpecificCause().getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
