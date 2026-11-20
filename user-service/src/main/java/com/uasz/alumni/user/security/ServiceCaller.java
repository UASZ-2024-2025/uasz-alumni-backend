package com.uasz.alumni.user.security;

/** Principal d'un appel inter-services authentifié par jeton de service (ADR-0010) : pas d'utilisateur. */
public record ServiceCaller(String serviceName) {
}
