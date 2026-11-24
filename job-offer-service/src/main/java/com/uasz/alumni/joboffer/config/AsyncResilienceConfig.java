package com.uasz.alumni.joboffer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Appels inter-services non bloquants (ADR-0006) : exécution asynchrone ({@code @Async}) sur un pool dédié et
 * retry déclaratif de Spring Framework 7 ({@code @Retryable}).
 */
@Configuration
@EnableAsync
@EnableResilientMethods
public class AsyncResilienceConfig {

    /** Nom référencé par {@code @Async} dans les listeners qui appellent d'autres microservices. */
    public static final String INTER_SERVICE_EXECUTOR = "interServiceExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncResilienceConfig.class);

    /**
     * Si la file est pleine, l'appel est abandonné (journalisé) sans faire échouer l'opération métier,
     * déjà validée.
     */
    @Bean(name = INTER_SERVICE_EXECUTOR)
    ThreadPoolTaskExecutor interServiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("inter-service-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("File des appels inter-services saturée : un appel n'a pas été effectué"));
        return executor;
    }
}
