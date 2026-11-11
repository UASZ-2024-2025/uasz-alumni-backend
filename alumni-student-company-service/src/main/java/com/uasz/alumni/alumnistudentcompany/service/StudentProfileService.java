package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentSpecifications;
import com.uasz.alumni.alumnistudentcompany.security.CurrentUser;
import com.uasz.alumni.alumnistudentcompany.security.CurrentUserProvider;
import com.uasz.alumni.alumnistudentcompany.spi.model.AvailabilityStatus;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfileUpdateRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_ITEMS = 30;
    static final int MAX_ITEM_LENGTH = 50;
    static final int MAX_SPECIALTY_LENGTH = 100;
    static final int MAX_BIO_LENGTH = 2000;

    private final StudentProfileRepository repository;
    private final CurrentUserProvider currentUserProvider;

    public StudentProfileService(StudentProfileRepository repository, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public StudentPagedResponse search(String query, String specialty, Integer graduationYear,
            AvailabilityStatus availability, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0) {
            throw badRequest("page doit être supérieur ou égal à 0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("size doit être compris entre 1 et " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("lastName", "firstName", "id"));
        Page<StudentProfileEntity> result = repository.findAll(
                StudentSpecifications.search(query, specialty, graduationYear, availability), pageable);

        List<StudentProfile> content = result.getContent().stream().map(StudentProfileService::toDto).toList();
        return new StudentPagedResponse()
                .content(content)
                .totalElements((int) Math.min(result.getTotalElements(), Integer.MAX_VALUE))
                .page(pageNumber)
                .size(pageSize);
    }

    @Transactional(readOnly = true)
    public StudentProfile getById(UUID id) {
        return toDto(find(id));
    }

    /**
     * Mise à jour par le propriétaire du profil (userId du JWT) ou un
     * administrateur.
     * Champs absents (null) = inchangés ; disponibilité : si le statut est fourni,
     * statut ET date sont remplacés
     * (une date absente efface la date).
     */
    @Transactional
    public StudentProfile update(UUID id, StudentProfileUpdateRequest request) {
        StudentProfileEntity entity = find(id);
        CurrentUser user = currentUserProvider.require();
        if (!user.admin() && !user.userId().equals(entity.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seul l'étudiant concerné ou un administrateur peut modifier ce profil");
        }

        if (request.getSpecialty() != null) {
            String specialty = request.getSpecialty().trim();
            if (specialty.length() > MAX_SPECIALTY_LENGTH) {
                throw badRequest("specialty : " + MAX_SPECIALTY_LENGTH + " caractères maximum");
            }
            entity.setSpecialty(specialty.isEmpty() ? null : specialty);
        }
        if (request.getAcademicYear() != null) {
            int year = request.getAcademicYear();
            if (year < 1 || year > 2100) {
                throw badRequest("academicYear doit être compris entre 1 et 2100");
            }
            entity.setAcademicYear(year);
        }
        if (request.getBio() != null) {
            String bio = request.getBio().trim();
            if (bio.length() > MAX_BIO_LENGTH) {
                throw badRequest("bio : " + MAX_BIO_LENGTH + " caractères maximum");
            }
            entity.setBio(bio.isEmpty() ? null : bio);
        }
        // Le modèle du contrat initialise ces listes à [] : « absent » et « vide » sont
        // indiscernables.
        // PUT = remplacement : les listes envoyées remplacent toujours les anciennes.
        entity.setSkills(cleanList("skills", request.getSkills()));
        entity.setInterests(cleanList("interests", request.getInterests()));
        if (request.getAvailabilityStatus() != null) {
            entity.setAvailabilityStatus(request.getAvailabilityStatus());
            entity.setAvailabilityDate(request.getAvailabilityDate());
        } else if (request.getAvailabilityDate() != null) {
            entity.setAvailabilityDate(request.getAvailabilityDate());
        }
        return toDto(repository.save(entity));
    }

    /**
     * Création du profil à l'inscription d'un ETUDIANT (idempotent). À appeler
     * depuis le point d'intégration
     * avec le user-service (événement ou appel interne) : ce n'est pas un endpoint
     * du contrat.
     */
    @Transactional
    public StudentProfile createForUser(UUID userId, String firstName, String lastName, String email) {
        return toDto(repository.findByUserId(userId)
                .orElseGet(() -> repository.save(new StudentProfileEntity(userId, firstName, lastName, email))));
    }

    private StudentProfileEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND",
                "Profil étudiant introuvable : " + id));
    }

    /**
     * Trim, suppression des vides et des doublons (insensible à la casse), limites
     * de taille.
     */
    private static List<String> cleanList(String field, List<String> values) {
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.length() > MAX_ITEM_LENGTH) {
                throw badRequest(field + " : chaque élément fait au plus " + MAX_ITEM_LENGTH + " caractères");
            }
            if (seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                cleaned.add(trimmed);
            }
        }
        if (cleaned.size() > MAX_ITEMS) {
            throw badRequest(field + " : " + MAX_ITEMS + " éléments maximum");
        }
        return cleaned;
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static StudentProfile toDto(StudentProfileEntity e) {
        return new StudentProfile()
                .id(e.getId())
                .userId(e.getUserId())
                .firstName(e.getFirstName())
                .lastName(e.getLastName())
                .email(e.getEmail())
                .specialty(e.getSpecialty())
                .academicYear(e.getAcademicYear())
                .bio(e.getBio())
                .interests(new ArrayList<>(e.getInterests()))
                .skills(new ArrayList<>(e.getSkills()))
                .availabilityStatus(e.getAvailabilityStatus())
                .availabilityDate(e.getAvailabilityDate());
    }
}