package com.uasz.alumni.analyticsmatching.service;

import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniEmploymentRecord;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Diplômés lus dans alumni-student-company-service. Toute défaillance du service appelé (réseau, 5xx, jeton refusé)
 * devient un 503 {@code ALUMNI_SERVICE_UNAVAILABLE} déclaré dans le contrat : on ne renvoie jamais un rapport faux.
 */
@Component
public class AlumniServiceInsertionDataSource implements InsertionDataSource {

    private static final Logger log = LoggerFactory.getLogger(AlumniServiceInsertionDataSource.class);

    private final AlumniEmploymentClient client;

    public AlumniServiceInsertionDataSource(AlumniEmploymentClient client) {
        this.client = client;
    }

    @Override
    public List<GraduateEmployment> graduates(Integer graduationYear, String specialty) {
        List<AlumniEmploymentRecord> records;
        try {
            records = client.list(graduationYear, specialty);
        } catch (RuntimeException e) {
            log.warn("Lecture des diplômés impossible : {}", e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ALUMNI_SERVICE_UNAVAILABLE",
                    "Les données des diplômés sont momentanément indisponibles : réessayez plus tard");
        }
        return records.stream()
                .filter(r -> r.getGraduationYear() != null && r.getSpecialty() != null)
                .map(r -> new GraduateEmployment(r.getGraduationYear(), r.getSpecialty(),
                        Boolean.TRUE.equals(r.getEmployed()), r.getExperienceStartDates()))
                .toList();
    }
}
