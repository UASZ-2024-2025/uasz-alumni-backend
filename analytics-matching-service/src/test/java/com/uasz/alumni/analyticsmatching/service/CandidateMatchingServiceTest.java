package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.spi.model.AvailabilityStatus;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import com.uasz.alumni.analyticsmatching.client.JobOffersClient;
import com.uasz.alumni.analyticsmatching.client.StudentProfilesClient;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import com.uasz.alumni.analyticsmatching.security.AuthenticatedUser;
import com.uasz.alumni.analyticsmatching.spi.model.CandidateMatchResult;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class CandidateMatchingServiceTest {

    private final JobOffersClient offers = mock(JobOffersClient.class);
    private final StudentProfilesClient students = mock(StudentProfilesClient.class);
    private final CandidateMatchingService service = new CandidateMatchingService(offers, students);

    private final UUID authorId = UUID.randomUUID();
    private final AuthenticatedUser author = new AuthenticatedUser(authorId, "rh@uasz.sn", "ENTREPRISE");
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR");
    private final UUID offerId = UUID.randomUUID();

    private final StudentProfile complete = student("Awa", "Ndiaye", AvailabilityStatus.AVAILABLE_FOR_JOB, "Java", "Spring Boot");
    private final StudentProfile partial = student("Moussa", "Diop", AvailabilityStatus.AVAILABLE_IMMEDIATELY, "Java");
    private final StudentProfile unavailable = student("Fatou", "Sow", AvailabilityStatus.NOT_AVAILABLE, "Java", "Spring Boot");
    private final StudentProfile unknownAvailability = student("Ibou", "Ba", null, "Java", "Spring Boot");

    @BeforeEach
    void setUp() {
        JobOffer offer = new JobOffer().id(offerId).title("Développeur Java").type(OfferType.JOB)
                .publisherId(authorId).requiredSkills(List.of("Java", "Spring Boot"));
        when(offers.find(offerId)).thenReturn(Optional.of(offer));
        when(students.listAll()).thenReturn(List.of(partial, unavailable, complete, unknownAvailability));
    }

    @Test
    void etudiantsDisponiblesAuDessusDuSeuilTriesParScore() {
        List<CandidateMatchResult> results = service.matchingCandidates(offerId, 50, author);

        assertThat(results).extracting(CandidateMatchResult::getStudentName).containsExactly("Awa Ndiaye", "Moussa Diop");
        assertThat(results).allSatisfy(r -> assertThat(r.getMatchScore()).isGreaterThanOrEqualTo(50));
        assertThat(results.getFirst().getStudentId()).isEqualTo(complete.getUserId());
        assertThat(results.getFirst().getMatchingSkills()).containsExactly("Java", "Spring Boot");
    }

    @Test
    void seuilParDefautEst60() {
        // Moussa : 35 (1 compétence sur 2) + 10 (intérêts neutres) + 10 (disponible immédiatement) = 55
        assertThat(service.matchingCandidates(offerId, null, author))
                .extracting(CandidateMatchResult::getStudentName).containsExactly("Awa Ndiaye");
    }

    @Test
    void administrateurAutorise() {
        assertThat(service.matchingCandidates(offerId, 0, admin)).hasSize(2);
    }

    @Test
    void autreRecruteurRecoit403() {
        AuthenticatedUser other = new AuthenticatedUser(UUID.randomUUID(), "autre@uasz.sn", "ENTREPRISE");
        assertThatThrownBy(() -> service.matchingCandidates(offerId, 50, other))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(students);
    }

    @Test
    void offreInconnueRecoit404() {
        UUID unknown = UUID.randomUUID();
        when(offers.find(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.matchingCandidates(unknown, 50, admin))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void minScoreHorsBornesRecoit400() {
        assertThatThrownBy(() -> service.matchingCandidates(offerId, -1, author))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void serviceSourceInjoignableRecoit503() {
        when(students.listAll()).thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> service.matchingCandidates(offerId, 50, author))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static StudentProfile student(String first, String last, AvailabilityStatus availability, String... skills) {
        return new StudentProfile().id(UUID.randomUUID()).userId(UUID.randomUUID()).firstName(first).lastName(last)
                .specialty("Génie Logiciel").skills(List.of(skills)).interests(List.of())
                .availabilityStatus(availability);
    }
}
