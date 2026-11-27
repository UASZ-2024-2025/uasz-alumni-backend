package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.model.Experience;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniSpecifications;
import com.uasz.alumni.alumnistudentcompany.repository.ExperienceRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniEmploymentRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Situation d'emploi des diplômés pour analytics-matching-service (ALUM-183, GET /internal/alumni/employment).
 * Aucune donnée personnelle n'est renvoyée : seuls l'année, la spécialité, l'état d'emploi et les dates de début
 * des expériences. Le calcul du taux est fait par le service appelant.
 */
@Service
public class AlumniEmploymentService {

    /** Taille des lots d'identifiants dans la clause IN (limite de paramètres de PostgreSQL). */
    static final int ID_CHUNK = 500;

    private final AlumniRepository alumni;
    private final ExperienceRepository experiences;

    public AlumniEmploymentService(AlumniRepository alumni, ExperienceRepository experiences) {
        this.alumni = alumni;
        this.experiences = experiences;
    }

    @Transactional(readOnly = true)
    public List<AlumniEmploymentRecord> list(Integer graduationYear, String specialty) {
        List<Alumni> graduates = alumni.findAll(AlumniSpecifications.graduates(graduationYear, specialty));
        if (graduates.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Experience>> byAlumni = experiencesByAlumni(graduates);
        return graduates.stream()
                .sorted(Comparator.comparing(Alumni::getGraduationYear, Comparator.<Integer>reverseOrder())
                        .thenComparing(a -> a.getSpecialty().strip(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Alumni::getId))
                .map(a -> toRecord(a, byAlumni.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    private Map<UUID, List<Experience>> experiencesByAlumni(List<Alumni> graduates) {
        List<UUID> ids = graduates.stream().map(Alumni::getId).toList();
        Map<UUID, List<Experience>> result = new HashMap<>();
        for (int from = 0; from < ids.size(); from += ID_CHUNK) {
            List<UUID> chunk = ids.subList(from, Math.min(from + ID_CHUNK, ids.size()));
            for (Experience e : experiences.findByAlumniIdIn(chunk)) {
                result.computeIfAbsent(e.getAlumniId(), k -> new ArrayList<>()).add(e);
            }
        }
        return result;
    }

    private static AlumniEmploymentRecord toRecord(Alumni a, List<Experience> experiences) {
        List<LocalDate> startDates = experiences.stream().map(Experience::getStartDate).sorted().toList();
        return new AlumniEmploymentRecord()
                .graduationYear(a.getGraduationYear())
                .specialty(a.getSpecialty().strip())
                .employed(experiences.stream().anyMatch(Experience::isCurrent))
                .experienceStartDates(startDates);
    }
}
