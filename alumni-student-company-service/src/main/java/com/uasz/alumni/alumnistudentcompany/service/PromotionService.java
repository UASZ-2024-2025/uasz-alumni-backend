package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.model.PromotionEntity;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.PromotionRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.Promotion;
import com.uasz.alumni.alumnistudentcompany.spi.model.PromotionCreateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotions académiques (ALUM-88). Les diplômés d'une promotion sont les Alumni dont l'année de diplôme et la
 * spécialité (sans tenir compte de la casse) sont celles de la promotion : {@code graduateCount} est calculé
 * avec la même règle que la liste, les deux correspondent donc toujours.
 */
@Service
public class PromotionService {

    static final int MIN_YEAR = 1990;
    static final int MAX_YEAR = 2100;
    static final int SPECIALTY_MAX = 150;

    private final PromotionRepository promotions;
    private final AlumniRepository alumni;

    public PromotionService(PromotionRepository promotions, AlumniRepository alumni) {
        this.promotions = promotions;
        this.alumni = alumni;
    }

    @Transactional(readOnly = true)
    public List<Promotion> list() {
        return promotions.findAllByOrderByAcademicYearDescSpecialtyAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public Promotion create(PromotionCreateRequest request) {
        Integer year = request.getAcademicYear();
        String specialty = request.getSpecialty() == null ? "" : request.getSpecialty().trim();
        if (year == null || year < MIN_YEAR || year > MAX_YEAR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACADEMIC_YEAR",
                    "L'année académique doit être comprise entre " + MIN_YEAR + " et " + MAX_YEAR + ".");
        }
        if (specialty.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SPECIALTY", "La spécialité est obligatoire.");
        }
        if (specialty.length() > SPECIALTY_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SPECIALTY",
                    "La spécialité ne doit pas dépasser " + SPECIALTY_MAX + " caractères.");
        }
        if (promotions.existsByAcademicYearAndSpecialtyIgnoreCase(year, specialty)) {
            throw duplicate(year, specialty);
        }
        try {
            return toDto(promotions.saveAndFlush(new PromotionEntity(year, specialty)));
        } catch (DataIntegrityViolationException e) {
            // deux créations simultanées : l'index unique (année, spécialité) tranche
            throw duplicate(year, specialty);
        }
    }

    @Transactional(readOnly = true)
    public List<StudentProfile> graduates(UUID promotionId) {
        PromotionEntity promotion = promotions.findById(promotionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROMOTION_NOT_FOUND", "Promotion introuvable."));
        return alumni.findByGraduationYearAndSpecialtyIgnoreCaseOrderByLastNameAscFirstNameAsc(
                        promotion.getAcademicYear(), promotion.getSpecialty())
                .stream().map(PromotionService::toGraduate).toList();
    }

    private Promotion toDto(PromotionEntity p) {
        long count = alumni.countByGraduationYearAndSpecialtyIgnoreCase(p.getAcademicYear(), p.getSpecialty());
        return new Promotion()
                .id(p.getId())
                .academicYear(p.getAcademicYear())
                .specialty(p.getSpecialty())
                .graduateCount(Math.toIntExact(count));
    }

    private static StudentProfile toGraduate(Alumni a) {
        return new StudentProfile()
                .id(a.getId())
                .userId(a.getUserId())
                .firstName(a.getFirstName())
                .lastName(a.getLastName())
                .email(a.getEmail())
                .specialty(a.getSpecialty())
                .academicYear(a.getGraduationYear());
    }

    private static ApiException duplicate(Integer year, String specialty) {
        return new ApiException(HttpStatus.CONFLICT, "PROMOTION_ALREADY_EXISTS",
                "La promotion " + specialty + " " + year + " existe déjà.");
    }
}
