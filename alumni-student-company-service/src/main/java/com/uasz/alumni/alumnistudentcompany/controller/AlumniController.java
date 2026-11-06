package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlumniController implements AlumniApi {

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
    public ResponseEntity<RecommendationResponse> recommendCandidate(RecommendationRequest recommendationRequest) {
        // TODO : enregistrer la recommandation (recommenderId = utilisateur courant) -> 201 / 400
        throw new NotImplementedYetException("recommendCandidate");
    }
}
