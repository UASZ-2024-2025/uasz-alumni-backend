package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StudentProfileRepository
        extends JpaRepository<StudentProfileEntity, UUID>, JpaSpecificationExecutor<StudentProfileEntity> {
    Optional<StudentProfileEntity> findByUserId(UUID userId);
}