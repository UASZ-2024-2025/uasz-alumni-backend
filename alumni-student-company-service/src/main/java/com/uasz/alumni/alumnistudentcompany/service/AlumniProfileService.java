package com.uasz.alumni.alumnistudentcompany.service;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniSpecifications;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniPagedResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniProfile;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniProfileUpdateRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultation et mise à jour du profil Alumni (ALUM-100). */
@Service
public class AlumniProfileService {

    static final int MAX_SPECIALTY_LENGTH = 100;
    static final int MAX_URL_LENGTH = 255;
    static final int MAX_PAGE_SIZE = 100;

    private final AlumniRepository alumniRepository;

    public AlumniProfileService(AlumniRepository alumniRepository) {
        this.alumniRepository = alumniRepository;
    }

    @Transactional(readOnly = true)
    public AlumniProfile getById(UUID id) {
        return toDto(find(id));
    }
    @Transactional(readOnly = true)
    public AlumniPagedResponse search(String query, Integer graduationYear, String specialty,
                                      Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0) {
            throw badRequest("page doit être supérieur ou égal à 0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("size doit être compris entre 1 et " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("lastName", "firstName", "id"));
        Page<Alumni> result = alumniRepository.findAll(
            AlumniSpecifications.search(query, graduationYear, specialty), pageable);

        List<AlumniProfile> content = result.getContent().stream().map(AlumniProfileService::toDto).toList();
        return new AlumniPagedResponse()
            .content(content)
            .totalElements((int) Math.min(result.getTotalElements(), Integer.MAX_VALUE))
            .page(pageNumber)
            .size(pageSize);
    }

    /**
     * Mise à jour du profil par son propriétaire uniquement.
     *
     * @param callerUserId identifiant du compte appelant (JWT)
     */
    @Transactional
    public AlumniProfile update(UUID id, AlumniProfileUpdateRequest request, UUID callerUserId) {
        Alumni alumni = find(id);
        if (!alumni.getUserId().equals(callerUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Seul le propriétaire du profil peut le modifier");
        }

        Integer year = request.getGraduationYear();
        if (year != null && (year < 1900 || year > 2100)) {
            throw badRequest("graduationYear doit être compris entre 1900 et 2100");
        }
        String specialty = clean("specialty", request.getSpecialty(), MAX_SPECIALTY_LENGTH);
        String linkedinUrl = clean("linkedinUrl", request.getLinkedinUrl(), MAX_URL_LENGTH);
        if (linkedinUrl != null && !linkedinUrl.matches("(?i)^https?://.+")) {
            throw badRequest("linkedinUrl doit commencer par http:// ou https://");
        }

        alumni.updateProfile(year, specialty, linkedinUrl);
        return toDto(alumniRepository.save(alumni));
    }

    private Alumni find(UUID id) {
        return alumniRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
            "ALUMNI_NOT_FOUND", "Alumni introuvable : " + id));
    }

    /** Null = inchangé ; trim ; limite de taille. */
    private static String clean(String field, String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw badRequest(field + " : " + max + " caractères maximum");
        }
        return trimmed;
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static AlumniProfile toDto(Alumni a) {
        return new AlumniProfile()
            .id(a.getId())
            .userId(a.getUserId())
            .firstName(a.getFirstName())
            .lastName(a.getLastName())
            .email(a.getEmail())
            .graduationYear(a.getGraduationYear())
            .specialty(a.getSpecialty())
            .currentCompany(a.getCurrentCompany())
            .currentPosition(a.getCurrentPosition())
            .linkedinUrl(a.getLinkedinUrl());
    }
}
