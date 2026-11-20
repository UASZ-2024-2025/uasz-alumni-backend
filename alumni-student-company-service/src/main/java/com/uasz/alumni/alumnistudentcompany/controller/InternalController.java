package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.service.CompanyService;
import com.uasz.alumni.alumnistudentcompany.service.ProfileProvisioningService;
import com.uasz.alumni.alumnistudentcompany.spi.api.InternalApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.Company;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.ProfileProvisioningResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opérations réservées aux autres microservices : jeton de service obligatoire (voir {@code SecurityConfig}).
 * {@code POST /internal/profiles} est appelé par user-service après la confirmation de l'email (ALUM-83) ;
 * {@code GET /internal/companies/{id}} par job-offer-service avant la publication d'une offre (ALUM-148).
 */
@RestController
public class InternalController implements InternalApi {

    private final ProfileProvisioningService provisioning;
    private final CompanyService companies;

    public InternalController(ProfileProvisioningService provisioning, CompanyService companies) {
        this.provisioning = provisioning;
        this.companies = companies;
    }

    @Override
    public ResponseEntity<ProfileProvisioningResponse> provisionProfile(ProfileProvisioningRequest request) {
        ProfileProvisioningService.Result result = provisioning.provision(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.profile());
    }

    @Override
    public ResponseEntity<Company> getCompanyForService(UUID id) {
        return ResponseEntity.ok(companies.get(id));
    }
}
