package com.uasz.alumni.user.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Beans transverses : propriétés, horloge (injectable dans les tests), hachage des mots de passe. */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, JwtProperties.class, AuthProperties.class})
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    /** Nom référencé par {@code @Async("mailExecutor")} dans les listeners d'emails. */
    public static final String MAIL_EXECUTOR = "mailExecutor";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // coût 12 ; les anciens hashes (coût 10) restent valides
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Pool dédié à l'envoi d'emails : la requête HTTP n'attend pas le serveur SMTP.
     * Si la file est pleine, l'email est abandonné (log) au lieu de faire échouer l'opération
     * métier ; l'utilisateur peut redemander un lien (resend-confirmation / forgot-password).
     */
    @Bean(name = MAIL_EXECUTOR)
    ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15); // < timeout-per-shutdown-phase (20s)
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("File d'envoi d'emails saturée : un email n'a pas été envoyé"));
        return executor;
    }
}
