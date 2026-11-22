package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.StudentCvEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCvRepository extends JpaRepository<StudentCvEntity, UUID> {
    Optional<StudentCvEntity> findByStudentId(UUID studentId);
}
