package com.uasz.alumni.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Ajoute API_PREFIX (/v1 en local, /user-service/v1 en K8s) devant tous les @RestController.
 * Les interfaces du SPI n'ont volontairement aucun prefixe (requestMappingMode=none).
 */
@Configuration
public class ApiPrefixConfig implements WebMvcConfigurer {

    private final String apiPrefix;

    public ApiPrefixConfig(@Value("${security.api.prefix}") String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(apiPrefix, HandlerTypePredicate.forAnnotation(RestController.class));
    }
}
