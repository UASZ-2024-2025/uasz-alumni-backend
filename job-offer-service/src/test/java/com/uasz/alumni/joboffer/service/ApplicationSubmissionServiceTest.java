package com.uasz.alumni.joboffer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.joboffer.exceptions.ApiException;
import com.uasz.alumni.joboffer.model.JobApplication;
import com.uasz.alumni.joboffer.model.Offer;
import com.uasz.alumni.joboffer.repository.JobApplicationRepository;
import com.uasz.alumni.joboffer.repository.OfferRepository;
import com.uasz.alumni.joboffer.security.AuthenticatedUser;
import com.uasz.alumni.joboffer.spi.model.ApplicationCreateRequest;
import com.uasz.alumni.joboffer.spi.model.ApplicationStatus;
import com.uasz.alumni.joboffer.spi.model.OfferStatus;
import com.uasz.alumni.joboffer.spi.model.OfferType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

class ApplicationSubmissionServiceTest {

    private final OfferRepository offers = mock(OfferRepository.class);
    private final JobApplicationRepository applications = mock(JobApplicationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ApplicationSubmissionService service = new ApplicationSubmissionService(offers, applications, events);

    private final UUID recruiter = UUID.randomUUID();
    private final AuthenticatedUser awa =
            new AuthenticatedUser(UUID.randomUUID(), "awa@zig.univ.sn", "ETUDIANT", "Awa Ndiaye", null);
    private Offer offer;

    @BeforeEach
    void setUp() {
        offer = new Offer("Développeur Java", OfferType.JOB, "desc", UUID.randomUUID(), recruiter, "Dakar");
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
    }

    private ApplicationCreateRequest request(UUID studentId) {
        return new ApplicationCreateRequest(studentId).cvId(UUID.randomUUID()).coverLetter("  Motivée !  ");
    }

    @Test
    void unEtudiantPostule_etLeRecruteurEstPrevenu() {
        ApplicationCreateRequest req = request(awa.id());

        JobApplication created = service.submit(offer.getId(), req, awa);

        assertThat(created.getStudentId()).isEqualTo(awa.id());
        assertThat(created.getStudentName()).isEqualTo("Awa Ndiaye");
        assertThat(created.getCvId()).isEqualTo(req.getCvId());
        assertThat(created.getCoverLetter()).isEqualTo("Motivée !");
        assertThat(created.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        verify(applications).saveAndFlush(created);
        ArgumentCaptor<ApplicationSubmitted> event = ArgumentCaptor.forClass(ApplicationSubmitted.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().recruiterId()).isEqualTo(recruiter);
        assertThat(event.getValue().studentName()).isEqualTo("Awa Ndiaye");
    }

    @Test
    void uneDeuxiemeCandidatureSurLaMemeOffreRenvoie409() {
        when(applications.existsByOfferIdAndStudentId(offer.getId(), awa.id())).thenReturn(true);

        assertStatus(() -> service.submit(offer.getId(), request(awa.id()), awa), HttpStatus.CONFLICT);
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void unDoubleClicSimultaneRenvoieAussi409() {
        when(applications.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertStatus(() -> service.submit(offer.getId(), request(awa.id()), awa), HttpStatus.CONFLICT);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void uneOffreFermeeOuEchueEstRefusee() {
        offer.changeStatus(OfferStatus.CLOSED);
        assertStatus(() -> service.submit(offer.getId(), request(awa.id()), awa), HttpStatus.BAD_REQUEST);

        offer.changeStatus(OfferStatus.OPEN);
        offer.completeDetails(null, null, null, List.of(), null, null, LocalDate.now().minusDays(1));
        assertStatus(() -> service.submit(offer.getId(), request(awa.id()), awa), HttpStatus.BAD_REQUEST);
    }

    @Test
    void seulUnEtudiantPostule_etSeulementPourLuiMeme() {
        AuthenticatedUser alumni = new AuthenticatedUser(UUID.randomUUID(), "a@uasz.sn", "ALUMNI", "Ali", null);
        assertStatus(() -> service.submit(offer.getId(), request(alumni.id()), alumni), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.submit(offer.getId(), request(UUID.randomUUID()), awa), HttpStatus.FORBIDDEN);
    }

    @Test
    void offreInconnue404_lettreTropLongue400() {
        assertStatus(() -> service.submit(UUID.randomUUID(), request(awa.id()), awa), HttpStatus.NOT_FOUND);
        ApplicationCreateRequest longue = request(awa.id()).coverLetter("x".repeat(5001));
        assertStatus(() -> service.submit(offer.getId(), longue, awa), HttpStatus.BAD_REQUEST);
    }

    @Test
    void sansNomDansLeJeton_lEmailSertDeNom() {
        AuthenticatedUser sansNom = new AuthenticatedUser(UUID.randomUUID(), "moussa@zig.univ.sn", "ETUDIANT", null, null);
        assertThat(service.submit(offer.getId(), request(sansNom.id()), sansNom).getStudentName())
                .isEqualTo("moussa@zig.univ.sn");
    }

    // ---------- suivi ----------

    @Test
    void lEtudiantNeVoitQueSesCandidatures() {
        Page<JobApplication> page = new PageImpl<>(List.of());
        when(applications.findByStudentId(eq(awa.id()), any(Pageable.class))).thenReturn(page);
        when(applications.findByStudentIdAndStatus(eq(awa.id()), eq(ApplicationStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(page);

        assertThat(service.listMine(null, null, null, null, awa)).isSameAs(page);
        assertThat(service.listMine(awa.id(), ApplicationStatus.SUBMITTED, 0, 10, awa)).isSameAs(page);
        assertStatus(() -> service.listMine(UUID.randomUUID(), null, 0, 20, awa), HttpStatus.FORBIDDEN);
    }

    @Test
    void lAdministrateurVoitToutes_lesAutresRolesSontRefuses() {
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@uasz.sn", "ADMINISTRATEUR", null, null);
        Page<JobApplication> page = new PageImpl<>(List.of());
        when(applications.findAllBy(any(Pageable.class))).thenReturn(page);

        assertThat(service.listMine(null, null, 0, 20, admin)).isSameAs(page);
        AuthenticatedUser entreprise = new AuthenticatedUser(UUID.randomUUID(), "rh@x.sn", "ENTREPRISE", null, null);
        assertStatus(() -> service.listMine(null, null, 0, 20, entreprise), HttpStatus.FORBIDDEN);
    }

    @Test
    void paginationInvalideRenvoie400() {
        assertStatus(() -> service.listMine(null, null, -1, 20, awa), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listMine(null, null, 0, 101, awa), HttpStatus.BAD_REQUEST);
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(expected));
    }
}
