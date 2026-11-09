package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.model.Experience;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.ExperienceRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.WorkExperienceRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parcours professionnel des Alumni.
 * Règle : au plus une expérience courante (isCurrent) par Alumni ; elle alimente
 * currentCompany / currentPosition du profil (données reprises par analytics-matching-service
 * pour le taux d'insertion).
 */
@Service
public class AlumniExperienceService {

    private final AlumniRepository alumniRepository;
    private final ExperienceRepository experienceRepository;

    public AlumniExperienceService(AlumniRepository alumniRepository, ExperienceRepository experienceRepository) {
        this.alumniRepository = alumniRepository;
        this.experienceRepository = experienceRepository;
    }

    @Transactional(readOnly = true)
    public List<Experience> listExperiences(UUID alumniId) {
        if (!alumniRepository.existsById(alumniId)) {
            throw alumniNotFound(alumniId);
        }
        return experienceRepository.findByAlumniIdOrderByStartDateDesc(alumniId);
    }

    /**
     * Ajoute une expérience au parcours de l'Alumni appelant (un Alumni n'ajoute que ses propres expériences).
     *
     * @param callerUserId identifiant du compte appelant (claim "sub" du JWT)
     */
    @Transactional
    public Experience addExperience(UUID alumniId, WorkExperienceRequest request, UUID callerUserId) {
        Alumni alumni = alumniRepository.findByIdForUpdate(alumniId).orElseThrow(() -> alumniNotFound(alumniId));
        if (!alumni.getUserId().equals(callerUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seul le propriétaire du profil peut ajouter une expérience");
        }

        boolean current = Boolean.TRUE.equals(request.getIsCurrent());
        validateDates(request, current);

        if (current) {
            // Une seule isCurrent = true à la fois : l'ancienne expérience courante ne l'est plus.
            experienceRepository.findByAlumniIdAndCurrentTrue(alumniId).forEach(Experience::unmarkCurrent);
            // Hibernate exécute les INSERT avant les UPDATE au flush : on force les UPDATE d'abord,
            // sinon l'index unique uq_work_experiences_one_current serait violé.
            experienceRepository.flush();
            alumni.updateCurrentJob(request.getCompanyName(), request.getPosition());
        }

        return experienceRepository.save(new Experience(alumniId, request.getCompanyName(), request.getPosition(),
                request.getStartDate(), request.getEndDate(), current, request.getDescription()));
    }

    private static void validateDates(WorkExperienceRequest request, boolean current) {
        if (current && request.getEndDate() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPERIENCE",
                    "Une expérience courante ne peut pas avoir de date de fin");
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPERIENCE",
                    "La date de fin doit être postérieure ou égale à la date de début");
        }
    }

    private static ApiException alumniNotFound(UUID alumniId) {
        return new ApiException(HttpStatus.NOT_FOUND, "ALUMNI_NOT_FOUND", "Alumni introuvable : " + alumniId);
    }
}
