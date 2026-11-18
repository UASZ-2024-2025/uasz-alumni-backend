package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.Experience;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperienceRepository extends JpaRepository<Experience, UUID> {

    /** Parcours du plus récent au plus ancien. */
    List<Experience> findByAlumniIdOrderByStartDateDesc(UUID alumniId);

    List<Experience> findByAlumniIdAndCurrentTrue(UUID alumniId);
}
