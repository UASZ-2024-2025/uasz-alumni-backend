package com.uasz.alumni.user.controller;

import com.uasz.alumni.user.service.CompanyLinkService;
import com.uasz.alumni.user.spi.api.InternalApi;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserCompanyLinkRequest;
import com.uasz.alumni.user.utils.UserMapper;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opérations réservées aux autres microservices : jeton de service obligatoire (voir {@code SecurityConfig}).
 * {@code PUT /internal/users/{id}/company} est appelé par alumni-student-company-service (ALUM-168).
 */
@RestController
public class InternalController implements InternalApi {

    private final CompanyLinkService links;

    public InternalController(CompanyLinkService links) {
        this.links = links;
    }

    @Override
    public ResponseEntity<User> linkCompanyAccount(UUID id, UserCompanyLinkRequest userCompanyLinkRequest) {
        return ResponseEntity.ok(UserMapper.toApi(links.linkCompany(id, userCompanyLinkRequest.getCompanyId())));
    }
}
