package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
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

@RestController
public class AlumniController implements AlumniApi {

    private final RecommendationService recommendations;

    public AlumniController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @Override
    public ResponseEntity<AlumniPagedResponse> searchAlumni(String query, UUID companyId, Integer graduationYear,
                                                            Integer page, Integer size) {
        // TODO : recherche paginée (nom, entreprise, poste, année)
        throw new NotImplementedYetException("searchAlumni");
    }

    @Override
    public ResponseEntity<AlumniProfile> getAlumniById(UUID id) {
        // TODO : profil détaillé -> 200 / 404
        throw new NotImplementedYetException("getAlumniById");
    }

    @Override
    public ResponseEntity<AlumniProfile> updateAlumniProfile(UUID id, AlumniProfileUpdateRequest alumniProfileUpdateRequest) {
        // TODO : mise à jour du profil -> 200 / 400 / 404
        throw new NotImplementedYetException("updateAlumniProfile");
    }

    @Override
    public ResponseEntity<List<WorkExperience>> getAlumniExperiences(UUID id) {
        // TODO : parcours professionnel
        throw new NotImplementedYetException("getAlumniExperiences");
    }

    @Override
    public ResponseEntity<WorkExperience> addAlumniExperience(UUID id, WorkExperienceRequest workExperienceRequest) {
        // TODO : ajouter une expérience -> 201 / 400
        throw new NotImplementedYetException("addAlumniExperience");
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
}
