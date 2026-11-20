package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.service.StudentProfileService;
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

    private final StudentProfileService studentProfileService;

    public StudentsController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @Override
    public ResponseEntity<StudentPagedResponse> searchStudents(String query, String specialty, Integer graduationYear,
            AvailabilityStatus availability, Integer page, Integer size) {
        return ResponseEntity
                .ok(studentProfileService.search(query, specialty, graduationYear, availability, page, size));
    }

    @Override
    public ResponseEntity<StudentProfile> getStudentById(UUID id) {
        return ResponseEntity.ok(studentProfileService.getById(id));
    }

    @Override
    public ResponseEntity<StudentProfile> updateStudentProfile(UUID id,
            StudentProfileUpdateRequest studentProfileUpdateRequest) {
        return ResponseEntity.ok(studentProfileService.update(id, studentProfileUpdateRequest));
    }

    @Override
    public ResponseEntity<CvResponse> getStudentCv(UUID id) {
        throw new NotImplementedYetException("getStudentCv");
    }

    @Override
    public ResponseEntity<CvResponse> updateStudentCv(UUID id, CvRequest cvRequest) {
        throw new NotImplementedYetException("updateStudentCv");
    }
}
