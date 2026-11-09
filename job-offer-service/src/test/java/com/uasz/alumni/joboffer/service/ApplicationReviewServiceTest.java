package com.uasz.alumni.joboffer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.repository.JobApplicationRepository;
import com.uasz.alumni.joboffer.repository.OfferRepository;
import com.uasz.alumni.joboffer.service.ApplicationReviewService.Caller;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

class ApplicationReviewServiceTest {

    private final OfferRepository offers = mock(OfferRepository.class);
    private final JobApplicationRepository applications = mock(JobApplicationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ApplicationReviewService service = new ApplicationReviewService(offers, applications, events);

    private final UUID recruiter = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final Caller author = new Caller(recruiter, false, "jeton-recruteur");
    private final Caller stranger = new Caller(UUID.randomUUID(), false, "jeton-autre");
    private final Caller admin = new Caller(UUID.randomUUID(), true, "jeton-admin");

    private Offer offer;
    private JobApplication application;

    @BeforeEach
    void setUp() {
        offer = new Offer("Développeur Java", OfferType.JOB, "desc", UUID.randomUUID(), recruiter, "Dakar");
        application = new JobApplication(offer, student, "Awa Ndiaye", null, null);
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(applications.findByOfferIdOrderByCreatedAtDesc(offer.getId())).thenReturn(List.of(application));
        when(applications.findWithOfferById(application.getId())).thenReturn(Optional.of(application));
    }

    @Test
    void lAuteurDeLOffreVoitLesCandidatures() {
        assertEquals(List.of(application), service.listForOffer(offer.getId(), author));
    }

    @Test
    void unAdministrateurVoitLesCandidatures() {
        assertEquals(1, service.listForOffer(offer.getId(), admin).size());
    }

    @Test
    void unAutreUtilisateurNeVoitPasLesCandidatures() {
        ApiException ex = assertThrows(ApiException.class, () -> service.listForOffer(offer.getId(), stranger));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void offreInconnueDonne404() {
        UUID unknown = UUID.randomUUID();
        when(offers.findById(unknown)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.listForOffer(unknown, author));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void chaqueChangementDeStatutNotifieLEtudiant() {
        service.changeStatus(application.getId(), ApplicationStatus.UNDER_REVIEW, author);
        service.changeStatus(application.getId(), ApplicationStatus.SHORTLISTED, author);
        service.changeStatus(application.getId(), ApplicationStatus.ACCEPTED, author);

        ArgumentCaptor<ApplicationStatusChanged> captor = ArgumentCaptor.forClass(ApplicationStatusChanged.class);
        verify(events, org.mockito.Mockito.times(3)).publishEvent(captor.capture());
        assertEquals(List.of(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED, ApplicationStatus.ACCEPTED),
                captor.getAllValues().stream().map(ApplicationStatusChanged::newStatus).toList());
        captor.getAllValues().forEach(e -> {
            assertEquals(student, e.studentId());
            assertEquals(application.getId(), e.applicationId());
        });
        assertEquals(ApplicationStatus.ACCEPTED, application.getStatus());
    }

    @Test
    void unAutreUtilisateurNePeutPasChangerLeStatut() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.changeStatus(application.getId(), ApplicationStatus.UNDER_REVIEW, stranger));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals(ApplicationStatus.SUBMITTED, application.getStatus());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void transitionInterditeDonne400SansNotification() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.changeStatus(application.getId(), ApplicationStatus.ACCEPTED, author));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void candidatureInconnueDonne404() {
        UUID unknown = UUID.randomUUID();
        when(applications.findWithOfferById(unknown)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.changeStatus(unknown, ApplicationStatus.UNDER_REVIEW, author));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @ParameterizedTest(name = "{0} -> {1} autorisée : {2}")
    @CsvSource({
        "SUBMITTED, UNDER_REVIEW, true",
        "SUBMITTED, REJECTED, true",
        "SUBMITTED, SHORTLISTED, false",
        "SUBMITTED, ACCEPTED, false",
        "UNDER_REVIEW, SHORTLISTED, true",
        "UNDER_REVIEW, REJECTED, true",
        "UNDER_REVIEW, ACCEPTED, false",
        "SHORTLISTED, ACCEPTED, true",
        "SHORTLISTED, REJECTED, true",
        "SHORTLISTED, UNDER_REVIEW, false",
        "ACCEPTED, REJECTED, false",
        "REJECTED, UNDER_REVIEW, false",
        "SUBMITTED, SUBMITTED, false"
    })
    void cycleDeVieDesCandidatures(ApplicationStatus from, ApplicationStatus to, boolean allowed) {
        assertEquals(allowed, JobApplication.isTransitionAllowed(from, to));
    }
}
