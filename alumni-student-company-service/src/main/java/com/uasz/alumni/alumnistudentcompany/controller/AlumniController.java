package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.model.Recommendation;
import com.uasz.alumni.alumnistudentcompany.service.RecommendationService;
import com.uasz.alumni.alumnistudentcompany.spi.api.AlumniApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniProfile;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniProfileUpdateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.RecommendationRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.RecommendationResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.WorkExperience;
import com.uasz.alumni.alumnistudentcompany.spi.model.WorkExperienceRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AlumniController implements AlumniApi {

    private final RecommendationService recommendations;

    public AlumniController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNI')")
    public ResponseEntity<RecommendationResponse> recommendCandidate(RecommendationRequest request) {
        // Le contrôleur n'a pas accès au JWT par paramètre (signature imposée par le SPI) : on le lit dans le contexte.
        Jwt jwt = (Jwt) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        Recommendation r = recommendations.recommander(UUID.fromString(jwt.getSubject()),
            request.getStudentId(), request.getCompanyId(), request.getComments(), jwt.getTokenValue());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RecommendationResponse()
            .id(r.getId()).recommenderId(r.getRecommenderId()).studentId(r.getStudentId())
            .companyId(r.getCompanyId()).comments(r.getComments()).createdAt(r.getCreatedAt()));
    }

    // Autres endpoints du tag Alumni : tickets distincts.
    @Override
    public ResponseEntity<WorkExperience> addAlumniExperience(UUID id, WorkExperienceRequest body) { throw nonImplemente(); }
    @Override
    public ResponseEntity<AlumniProfile> getAlumniById(UUID id) { throw nonImplemente(); }
    @Override
    public ResponseEntity<List<WorkExperience>> getAlumniExperiences(UUID id) { throw nonImplemente(); }
    @Override
    public ResponseEntity<AlumniPagedResponse> searchAlumni(String q, UUID promotionId, Integer a, Integer b, Integer c) { throw nonImplemente(); }
    @Override
    public ResponseEntity<AlumniProfile> updateAlumniProfile(UUID id, AlumniProfileUpdateRequest body) { throw nonImplemente(); }

    private static ResponseStatusException nonImplemente() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
