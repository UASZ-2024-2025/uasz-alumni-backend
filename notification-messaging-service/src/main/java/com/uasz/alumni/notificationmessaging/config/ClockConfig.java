package com.uasz.alumni.notificationmessaging.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge injectable : les tests fixent l'heure (sourdine, ordre des messages, non-lus). */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
