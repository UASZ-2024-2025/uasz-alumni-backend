package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.model.StudentCvPhotoEntity;
import com.uasz.alumni.alumnistudentcompany.service.StudentCvPhotoService;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class StudentsController implements StudentsApi {

    private final StudentProfileService studentProfileService;
    private final StudentCvService studentCvService;
    private final StudentCvPhotoService studentCvPhotoService;

    public StudentsController(StudentProfileService studentProfileService, StudentCvService studentCvService,
            StudentCvPhotoService studentCvPhotoService) {
        this.studentProfileService = studentProfileService;
        this.studentCvService = studentCvService;
        this.studentCvPhotoService = studentCvPhotoService;
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

    @Override
    public ResponseEntity<Resource> getStudentCvPhoto(UUID id) {
        StudentCvPhotoEntity photo = studentCvPhotoService.get(id);
        // Donnée personnelle : jamais mise en cache par un proxy ni par le navigateur.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.noStore())
                .body(new ByteArrayResource(photo.getData()));
    }

    @Override
    public ResponseEntity<Void> updateStudentCvPhoto(UUID id, MultipartFile file) {
        studentCvPhotoService.replace(id, file);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteStudentCvPhoto(UUID id) {
        studentCvPhotoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
