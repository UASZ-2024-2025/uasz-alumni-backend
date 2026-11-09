package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.model.Experience;
import com.uasz.alumni.alumnistudentcompany.service.AlumniExperienceService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlumniController implements AlumniApi {

    private final AlumniExperienceService experiences;

    public AlumniController(AlumniExperienceService experiences) {
        this.experiences = experiences;
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
        return ResponseEntity.ok(experiences.listExperiences(id).stream().map(AlumniController::toDto).toList());
    }

    @Override
    @PreAuthorize("hasRole('ALUMNI')")
    public ResponseEntity<WorkExperience> addAlumniExperience(UUID id, WorkExperienceRequest workExperienceRequest) {
        // La signature imposée par le SPI ne donne pas accès au JWT : on lit l'appelant dans le contexte de sécurité.
        UUID callerUserId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        Experience saved = experiences.addExperience(id, workExperienceRequest, callerUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @Override
    public ResponseEntity<RecommendationResponse> recommendCandidate(RecommendationRequest recommendationRequest) {
        // TODO : enregistrer la recommandation (recommenderId = utilisateur courant) -> 201 / 400
        throw new NotImplementedYetException("recommendCandidate");
    }

    private static WorkExperience toDto(Experience e) {
        return new WorkExperience()
                .id(e.getId())
                .companyName(e.getCompanyName())
                .position(e.getPosition())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .isCurrent(e.isCurrent())
                .description(e.getDescription());
    }
}
