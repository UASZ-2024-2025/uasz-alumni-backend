package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.spi.api.StudentsApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.AvailabilityStatus;
import com.uasz.alumni.alumnistudentcompany.spi.model.CvRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.CvResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentPagedResponse;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfileUpdateRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentsController implements StudentsApi {

    @Override
    public ResponseEntity<StudentPagedResponse> searchStudents(String query, String specialty, Integer graduationYear,
                                                               AvailabilityStatus availability, Integer page, Integer size) {
        // TODO : recherche multi-critères paginée
        throw new NotImplementedYetException("searchStudents");
    }

    @Override
    public ResponseEntity<StudentProfile> getStudentById(UUID id) {
        // TODO : profil détaillé -> 200 / 404
        throw new NotImplementedYetException("getStudentById");
    }

    @Override
    public ResponseEntity<StudentProfile> updateStudentProfile(UUID id, StudentProfileUpdateRequest studentProfileUpdateRequest) {
        // TODO : mise à jour du profil (propriétaire) -> 200 / 400 / 404
        throw new NotImplementedYetException("updateStudentProfile");
    }

    @Override
    public ResponseEntity<CvResponse> getStudentCv(UUID id) {
        // TODO : CV de l'étudiant -> 200 / 404
        throw new NotImplementedYetException("getStudentCv");
    }

    @Override
    public ResponseEntity<CvResponse> updateStudentCv(UUID id, CvRequest cvRequest) {
        // TODO : créer/mettre à jour le CV -> 200 / 400 / 404
        throw new NotImplementedYetException("updateStudentCv");
    }
}
