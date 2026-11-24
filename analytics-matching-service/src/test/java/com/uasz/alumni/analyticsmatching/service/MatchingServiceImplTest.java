package com.uasz.alumni.analyticsmatching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.analyticsmatching.client.JobOfferServiceClient;
import com.uasz.alumni.analyticsmatching.client.StudentServiceClient;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.spi.model.OfferMatchResult;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MatchingServiceImplTest {

    private final StudentServiceClient studentClient = mock(StudentServiceClient.class);
    private final JobOfferServiceClient offerClient = mock(JobOfferServiceClient.class);
    private final MatchingServiceImpl service = new MatchingServiceImpl(studentClient, offerClient);

    @Test
    void recommandeUniquementOffresOpenTrieesParScoreDecroissant() {
        UUID studentId = UUID.randomUUID();
        StudentProfile student = new StudentProfile().id(studentId).skills(List.of("Java", "Spring Boot"));
        when(studentClient.getStudentProfile(studentId)).thenReturn(Optional.of(student));

        JobOffer perfect = new JobOffer().id(UUID.randomUUID()).title("Backend Java")
                .companyName("Atos").status(OfferStatus.OPEN).requiredSkills(List.of("Java", "Spring Boot"));
        JobOffer partial = new JobOffer().id(UUID.randomUUID()).title("Fullstack")
                .companyName("Sonatel").status(OfferStatus.OPEN).requiredSkills(List.of("Java", "React"));
        JobOffer low = new JobOffer().id(UUID.randomUUID()).title("Python")
                .companyName("Orange").status(OfferStatus.OPEN).requiredSkills(List.of("Python", "Django", "AWS"));
        JobOffer closed = new JobOffer().id(UUID.randomUUID()).title("Fermée")
                .companyName("ClosedCo").status(OfferStatus.CLOSED).requiredSkills(List.of("Java"));

        when(offerClient.getJobOffers(OfferStatus.OPEN)).thenReturn(List.of(partial, perfect, low, closed));

        List<OfferMatchResult> results = service.getMatchingOffersForStudent(studentId, 50);

        assertEquals(2, results.size());
        assertEquals("Backend Java", results.get(0).getOfferTitle());
        assertEquals(100, results.get(0).getMatchScore());
        assertEquals("Fullstack", results.get(1).getOfferTitle());
        assertEquals(50, results.get(1).getMatchScore());
    }

    @Test
    void etudiantInexistantRenvoie404() {
        UUID unknown = UUID.randomUUID();
        when(studentClient.getStudentProfile(unknown)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getMatchingOffersForStudent(unknown, 50));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
