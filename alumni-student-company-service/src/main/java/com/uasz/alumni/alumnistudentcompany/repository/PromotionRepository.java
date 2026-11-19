package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.PromotionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<PromotionEntity, UUID> {

    /** De la plus récente à la plus ancienne, puis par spécialité. */
    List<PromotionEntity> findAllByOrderByAcademicYearDescSpecialtyAsc();

    boolean existsByAcademicYearAndSpecialtyIgnoreCase(Integer academicYear, String specialty);
}
