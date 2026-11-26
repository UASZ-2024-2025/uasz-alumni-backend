package com.uasz.alumni.joboffer.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Beans transverses : horloge (injectable dans les tests) et tâches planifiées (fermeture des offres expirées). */
@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
