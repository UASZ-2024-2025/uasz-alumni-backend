package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.PromotionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PromotionRepository extends JpaRepository<PromotionEntity, UUID> {

    /** De la plus récente à la plus ancienne, puis par spécialité. */
    List<PromotionEntity> findAllByOrderByAcademicYearDescSpecialtyAsc();

    /** Avec lower(...), comme l'index unique uq_promotions_year_specialty (academic_year, LOWER(specialty)). */
    @Query("""
            select count(p) > 0 from PromotionEntity p
            where p.academicYear = :academicYear and lower(p.specialty) = lower(:specialty)""")
    boolean existsByAcademicYearAndSpecialtyIgnoreCase(Integer academicYear, String specialty);
}
