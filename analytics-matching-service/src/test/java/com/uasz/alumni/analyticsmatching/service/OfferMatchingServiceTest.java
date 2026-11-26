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
import com.uasz.alumni.analyticsmatching.spi.model.OfferMatchResult;
import com.uasz.alumni.joboffer.spi.model.JobOffer;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class OfferMatchingServiceTest {

    private final StudentProfilesClient students = mock(StudentProfilesClient.class);
    private final JobOffersClient offers = mock(JobOffersClient.class);
    private final OfferMatchingService service = new OfferMatchingService(students, offers);

    private final UUID userId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final AuthenticatedUser student = new AuthenticatedUser(userId, "etu@uasz.sn", "ETUDIANT");
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR");

    private final JobOffer javaJob = offer("Développeur Java", OfferType.JOB, "Java", "Spring Boot");
    private final JobOffer reactInternship = offer("Stage front", OfferType.INTERNSHIP, "React", "TypeScript");
    private final JobOffer partial = offer("Back-end", OfferType.JOB, "Java", "Kafka");

    @BeforeEach
    void setUp() {
        StudentProfile profile = new StudentProfile().id(profileId).userId(userId)
                .skills(List.of("java", "spring boot")).interests(List.of())
                .availabilityStatus(AvailabilityStatus.AVAILABLE_FOR_JOB);
        when(students.find(profileId)).thenReturn(Optional.of(profile));
        when(students.find(userId)).thenReturn(Optional.of(profile));
        when(offers.listOpen()).thenReturn(List.of(reactInternship, partial, javaJob));
    }

    @Test
    void offresAuDessusDuSeuilTrieesParScoreDecroissant() {
        List<OfferMatchResult> results = service.matchingOffers(profileId, 50, student);

        assertThat(results).extracting(OfferMatchResult::getOfferTitle).containsExactly("Développeur Java", "Back-end");
        assertThat(results).allSatisfy(r -> assertThat(r.getMatchScore()).isGreaterThanOrEqualTo(50));
        // 70 (compétences) + 10 (aucun intérêt déclaré : neutre) + 10 (disponible pour un emploi)
        assertThat(results.getFirst().getMatchScore()).isEqualTo(90);
        assertThat(results.get(1).getMatchingSkills()).containsExactly("Java");
        assertThat(results.get(1).getMissingSkills()).containsExactly("Kafka");
    }

    @Test
    void seuilParDefautEst50() {
        assertThat(service.matchingOffers(userId, null, student))
                .extracting(OfferMatchResult::getOfferTitle).doesNotContain("Stage front");
    }

    @Test
    void administrateurConsulteNImporteQuelEtudiant() {
        assertThat(service.matchingOffers(profileId, 0, admin)).hasSize(3);
    }

    @Test
    void autreEtudiantRecoit403() {
        AuthenticatedUser other = new AuthenticatedUser(UUID.randomUUID(), "autre@uasz.sn", "ETUDIANT");
        assertThatThrownBy(() -> service.matchingOffers(profileId, 50, other))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void autreRoleRecoit403SansAppelDistant() {
        AuthenticatedUser recruiter = new AuthenticatedUser(UUID.randomUUID(), "rh@uasz.sn", "ENTREPRISE");
        assertThatThrownBy(() -> service.matchingOffers(profileId, 50, recruiter))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(students, offers);
    }

    @Test
    void etudiantInconnuRecoit404() {
        UUID unknown = UUID.randomUUID();
        when(students.find(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.matchingOffers(unknown, 50, admin))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void minScoreHorsBornesRecoit400() {
        assertThatThrownBy(() -> service.matchingOffers(profileId, 101, student))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void serviceSourceInjoignableRecoit503() {
        when(offers.listOpen()).thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> service.matchingOffers(profileId, 50, student))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static JobOffer offer(String title, OfferType type, String... skills) {
        return new JobOffer().id(UUID.randomUUID()).title(title).type(type).companyName("Gainde 2000")
                .requiredSkills(List.of(skills));
    }
}
