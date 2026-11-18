package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.service.ProfileProvisioningService;
import com.uasz.alumni.alumnistudentcompany.spi.api.InternalApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opérations réservées aux autres microservices : jeton de service obligatoire (voir {@code SecurityConfig}).
 * {@code POST /internal/profiles} est appelé par user-service après la confirmation de l'email (ALUM-83).
 */
@RestController
public class InternalController implements InternalApi {

    private final ProfileProvisioningService provisioning;

    public InternalController(ProfileProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public ResponseEntity<ProfileProvisioningResponse> provisionProfile(ProfileProvisioningRequest request) {
        ProfileProvisioningService.Result result = provisioning.provision(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.profile());
    }
}
