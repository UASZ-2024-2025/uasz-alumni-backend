package com.uasz.alumni.user.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Beans transverses : propriétés, horloge (injectable dans les tests), hachage des mots de passe. */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, JwtProperties.class, AuthProperties.class})
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // coût 12 ; les anciens hashes (coût 10) restent valides
        return new BCryptPasswordEncoder(12);
    }
}
