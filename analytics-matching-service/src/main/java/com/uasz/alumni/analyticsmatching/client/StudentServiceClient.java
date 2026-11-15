package com.uasz.alumni.analyticsmatching.client;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.util.Optional;
import java.util.UUID;

public interface StudentServiceClient {
    Optional<StudentProfile> getStudentProfile(UUID studentId);
}
