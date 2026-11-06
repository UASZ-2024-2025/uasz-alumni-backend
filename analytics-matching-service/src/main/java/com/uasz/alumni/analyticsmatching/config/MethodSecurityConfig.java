package com.uasz.alumni.analyticsmatching.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Active @PreAuthorize (règles de rôle par endpoint). Séparé de SecurityConfig pour ne pas entrer en conflit avec AL-15. */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
