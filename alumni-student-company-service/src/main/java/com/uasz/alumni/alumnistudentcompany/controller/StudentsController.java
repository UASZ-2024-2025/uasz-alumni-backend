package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.service.StudentCvService;
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
    private final StudentCvService studentCvService;

    public StudentsController(StudentProfileService studentProfileService, StudentCvService studentCvService) {
        this.studentProfileService = studentProfileService;
        this.studentCvService = studentCvService;
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
        return ResponseEntity.ok(studentCvService.get(id));
    }

    @Override
    public ResponseEntity<CvResponse> updateStudentCv(UUID id, CvRequest cvRequest) {
        return ResponseEntity.ok(studentCvService.update(id, cvRequest));
    }
}
