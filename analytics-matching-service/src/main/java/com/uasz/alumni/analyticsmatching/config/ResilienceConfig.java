package com.uasz.alumni.analyticsmatching.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/** Active le retry déclaratif de Spring Framework 7 ({@code @Retryable}) pour les appels inter-services (ADR-0006). */
@Configuration
@EnableResilientMethods
public class ResilienceConfig {
}
