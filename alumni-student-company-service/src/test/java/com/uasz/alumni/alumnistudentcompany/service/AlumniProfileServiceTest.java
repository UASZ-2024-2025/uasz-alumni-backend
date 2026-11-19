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
}
