package com.uasz.alumni.analyticsmatching.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** En-tête Authorization de la requête en cours, propagé aux services lus pour le matching (ADR-0006). */
final class CallerToken {

    private CallerToken() { }

    static String authorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String header = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null) {
                return header;
            }
        }
        throw new IllegalStateException("Aucun jeton à propager : appel hors requête HTTP");
    }
}
