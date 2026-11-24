package com.uasz.alumni.user.utils;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientIp {

    private ClientIp() {
    }

    /**
     * IP de la requête courante. Derrière l'ingress de confiance, FORWARD_HEADERS_STRATEGY=native
     * fournit la vraie IP (on ne lit jamais un en-tête X-Forwarded-For falsifiable à la main).
     */
    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
