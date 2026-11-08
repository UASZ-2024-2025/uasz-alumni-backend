package com.uasz.alumni.joboffer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Active l'exécution asynchrone (@Async) des appels inter-services non bloquants et le retry
 * déclaratif de Spring Framework 7 (@Retryable), voir ADR-0006.
 */
@Configuration
@EnableAsync
@EnableResilientMethods
public class AsyncResilienceConfig {
}
