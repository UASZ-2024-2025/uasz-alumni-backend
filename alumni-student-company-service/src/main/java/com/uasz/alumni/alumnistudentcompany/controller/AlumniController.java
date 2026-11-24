package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.model.Experience;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.service.AlumniExperienceService;
import com.uasz.alumni.alumnistudentcompany.service.AlumniHomeSummaryService;
import com.uasz.alumni.alumnistudentcompany.service.AlumniProfileService;
import com.uasz.alumni.alumnistudentcompany.spi.api.AlumniApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniHomeSummary;
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
    private final AlumniProfileService profiles;
    private final AlumniHomeSummaryService homeSummary;

    public AlumniController(AlumniExperienceService experiences, AlumniProfileService profiles,
                            AlumniHomeSummaryService homeSummary) {
        this.experiences = experiences;
        this.profiles = profiles;
        this.homeSummary = homeSummary;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNI')")
    public ResponseEntity<AlumniHomeSummary> getMyAlumniHomeSummary() {
        // L'Alumni est déduit du jeton uniquement (aucun identifiant en paramètre).
        return ResponseEntity.ok(homeSummary.summaryForCurrentUser());
    }

    @Override
    public ResponseEntity<AlumniPagedResponse> searchAlumni(String query, UUID companyId, Integer graduationYear,
                                                            String specialty, Integer page, Integer size) {
        // companyId : pas encore filtrable (l'entité n'a que le nom de l'entreprise)
        return ResponseEntity.ok(profiles.search(query, graduationYear, specialty, page, size));
    }

    @Override
    public ResponseEntity<AlumniProfile> getAlumniById(UUID id) {
        return ResponseEntity.ok(profiles.getById(id));
    }

    @Override
    @PreAuthorize("hasRole('ALUMNI')")
    public ResponseEntity<AlumniProfile> updateAlumniProfile(UUID id, AlumniProfileUpdateRequest alumniProfileUpdateRequest) {
        AuthenticatedUser caller = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(profiles.update(id, alumniProfileUpdateRequest, caller.id()));
    }

    @Override
    public ResponseEntity<List<WorkExperience>> getAlumniExperiences(UUID id) {
        return ResponseEntity.ok(experiences.listExperiences(id).stream().map(AlumniController::toDto).toList());
    }

    @Override
    @PreAuthorize("hasRole('ALUMNI')")
    public ResponseEntity<WorkExperience> addAlumniExperience(UUID id, WorkExperienceRequest workExperienceRequest) {
        // La signature imposée par le SPI ne donne pas accès au jeton : on lit l'appelant dans le contexte de sécurité.
        AuthenticatedUser caller = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Experience saved = experiences.addExperience(id, workExperienceRequest, caller.id());
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
