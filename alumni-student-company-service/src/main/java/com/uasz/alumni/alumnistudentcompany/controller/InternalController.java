package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.spi.api.InternalApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opérations réservées aux autres microservices (jeton de service, voir {@code SecurityConfig}).
 *
 * <p>{@code POST /internal/profiles} est appelé par user-service après la confirmation de l'email d'un
 * compte ETUDIANT ou ALUMNI (ALUM-83). La création effective du profil dépend des tables {@code students}
 * et {@code alumni_profiles}, introduites par les branches « profil étudiant » et « expériences Alumni »
 * encore en revue : en attendant leur fusion, l'opération répond 501. Côté user-service, un 501 n'est pas
 * rejoué et ne bloque pas l'inscription.
 */
@RestController
public class InternalController implements InternalApi {

    @Override
    public ResponseEntity<ProfileProvisioningResponse> provisionProfile(ProfileProvisioningRequest request) {
        // TODO (après fusion des tables de profils) : créer le profil ETUDIANT ou ALUMNI pour request.getUserId(),
        // idempotent : 201 si créé, 200 avec le profil existant si déjà présent.
        throw new NotImplementedYetException("provisionProfile");
    }
}
