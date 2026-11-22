package com.uasz.alumni.alumnistudentcompany.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniProfile;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniProfileUpdateRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniPagedResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class AlumniProfileServiceTest {

    private AlumniRepository repository;
    private AlumniProfileService service;

    private final UUID ownerUserId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private Alumni alumni;

    @BeforeEach
    void setUp() {
        repository = mock(AlumniRepository.class);
        service = new AlumniProfileService(repository);
        alumni = new Alumni(ownerUserId);
        when(repository.findById(alumni.getId())).thenReturn(Optional.of(alumni));
        when(repository.save(alumni)).thenReturn(alumni);
    }

    // ---------- getById ----------

    @Test
    void getByIdRetourneLeProfil() {
        AlumniProfile profile = service.getById(alumni.getId());

        assertEquals(alumni.getId(), profile.getId());
        assertEquals(ownerUserId, profile.getUserId());
    }

    @Test
    void getByIdAccepteAussiLIdentifiantDuCompte() {
        // « Mon profil » du front ne connaît que l'identifiant du compte (sub du JWT)
        when(repository.findById(ownerUserId)).thenReturn(Optional.empty());
        when(repository.findByUserId(ownerUserId)).thenReturn(Optional.of(alumni));

        AlumniProfile profile = service.getById(ownerUserId);

        assertEquals(alumni.getId(), profile.getId());
    }

    @Test
    void getByIdInexistantDonne404() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.getById(unknown));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---------- update ----------

    @Test
    void updateParProprietaireEnregistreLesChamps() {
        AlumniProfileUpdateRequest req = new AlumniProfileUpdateRequest()
            .graduationYear(2023)
            .specialty("  Génie Logiciel  ")
            .linkedinUrl("https://linkedin.com/in/mouhamed");

        AlumniProfile result = service.update(alumni.getId(), req, ownerUserId);

        assertEquals(2023, result.getGraduationYear());
        assertEquals("Génie Logiciel", result.getSpecialty());
        assertEquals("https://linkedin.com/in/mouhamed", result.getLinkedinUrl());
        verify(repository).save(alumni);
    }

    @Test
    void updateChampsAbsentsResteInchange() {
        service.update(alumni.getId(), new AlumniProfileUpdateRequest().specialty("IA"), ownerUserId);

        AlumniProfile result = service.update(alumni.getId(),
            new AlumniProfileUpdateRequest().graduationYear(2024), ownerUserId);

        assertEquals("IA", result.getSpecialty());
        assertEquals(2024, result.getGraduationYear());
    }

    @Test
    void updateParAutreUtilisateurDonne403() {
        AlumniProfileUpdateRequest req = new AlumniProfileUpdateRequest().specialty("IA");

        ApiException ex = assertThrows(ApiException.class,
            () -> service.update(alumni.getId(), req, otherUserId));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(repository, never()).save(alumni);
    }

    @Test
    void updateProfilInexistantDonne404() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
            () -> service.update(unknown, new AlumniProfileUpdateRequest(), ownerUserId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void updateAnneeInvalideDonne400() {
        ApiException ex = assertThrows(ApiException.class, () -> service.update(alumni.getId(),
            new AlumniProfileUpdateRequest().graduationYear(1800), ownerUserId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(repository, never()).save(alumni);
    }

    @Test
    void updateLinkedinSansHttpDonne400() {
        ApiException ex = assertThrows(ApiException.class, () -> service.update(alumni.getId(),
            new AlumniProfileUpdateRequest().linkedinUrl("linkedin.com/in/x"), ownerUserId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void updateSpecialiteTropLongueDonne400() {
        String tooLong = "x".repeat(AlumniProfileService.MAX_SPECIALTY_LENGTH + 1);

        ApiException ex = assertThrows(ApiException.class, () -> service.update(alumni.getId(),
            new AlumniProfileUpdateRequest().specialty(tooLong), ownerUserId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ---------- search ----------

    @SuppressWarnings("unchecked")
    @Test
    void searchRetourneUnePageDeProfils() {
        Page<Alumni> result = new PageImpl<>(List.of(alumni), org.springframework.data.domain.PageRequest.of(0, 20), 1);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(result);

        AlumniPagedResponse response = service.search("sow", 2023, "Génie Logiciel", 0, 20);

        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals(0, response.getPage());
        assertEquals(20, response.getSize());
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchValeursParDefautPageEtTaille() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        AlumniPagedResponse response = service.search(null, null, null, null, null);

        assertEquals(0, response.getPage());
        assertEquals(20, response.getSize());
    }

    @Test
    void searchPageNegativeDonne400() {
        ApiException ex = assertThrows(ApiException.class, () -> service.search(null, null, null, -1, 20));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void searchTailleZeroDonne400() {
        ApiException ex = assertThrows(ApiException.class, () -> service.search(null, null, null, 0, 0));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void searchTailleTropGrandeDonne400() {
        ApiException ex = assertThrows(ApiException.class, () -> service.search(null, null, null, 0, 101));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
