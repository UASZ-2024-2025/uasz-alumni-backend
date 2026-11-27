package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.StudentCvPhotoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Photos de CV, indexées par l'id du profil étudiant (ALUM-255). */
public interface StudentCvPhotoRepository extends JpaRepository<StudentCvPhotoEntity, UUID> {
}
