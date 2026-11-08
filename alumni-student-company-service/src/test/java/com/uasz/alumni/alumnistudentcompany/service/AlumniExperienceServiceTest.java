package com.uasz.alumni.alumnistudentcompany.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.model.Experience;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.ExperienceRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.WorkExperienceRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class AlumniExperienceServiceTest {

    private final AlumniRepository alumniRepository = mock(AlumniRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final AlumniExperienceService service = new AlumniExperienceService(alumniRepository, experienceRepository);

    private final UUID owner = UUID.randomUUID();
    private Alumni alumni;

    @BeforeEach
    void setUp() {
        alumni = new Alumni(owner);
        when(alumniRepository.findByIdForUpdate(alumni.getId())).thenReturn(Optional.of(alumni));
        when(alumniRepository.existsById(alumni.getId())).thenReturn(true);
        when(experienceRepository.save(any(Experience.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static WorkExperienceRequest request(String company, String position, LocalDate start) {
        return new WorkExperienceRequest(company, position, start);
    }

    @Test
    void ajouterUneExperienceCouranteMetAJourLePosteActuelDuProfil() {
        Experience saved = service.addExperience(alumni.getId(),
                request("Atos", "Architecte Logiciel", LocalDate.of(2024, 1, 1)).isCurrent(true), owner);

        assertTrue(saved.isCurrent());
        assertEquals("Atos", alumni.getCurrentCompany());
        assertEquals("Architecte Logiciel", alumni.getCurrentPosition());
    }

    @Test
    void uneNouvelleExperienceCouranteRemplaceLAncienne() {
        Experience previous = new Experience(alumni.getId(), "Sonatel", "Développeur",
                LocalDate.of(2021, 3, 1), null, true, null);
        when(experienceRepository.findByAlumniIdAndCurrentTrue(alumni.getId())).thenReturn(List.of(previous));

        service.addExperience(alumni.getId(),
                request("Atos", "Architecte", LocalDate.of(2024, 1, 1)).isCurrent(true), owner);

        assertFalse(previous.isCurrent());
        assertNull(previous.getEndDate());
        // Les UPDATE de l'ancienne expérience doivent partir avant l'INSERT de la nouvelle (index unique).
        InOrder order = inOrder(experienceRepository);
        order.verify(experienceRepository).flush();
        order.verify(experienceRepository).save(any(Experience.class));
    }

    @Test
    void uneExperiencePasseeNeModifiePasLePosteActuel() {
        alumni.updateCurrentJob("Atos", "Architecte");

        Experience saved = service.addExperience(alumni.getId(),
                request("Orange", "Stagiaire", LocalDate.of(2020, 1, 1)).endDate(LocalDate.of(2020, 6, 30)),
                owner);

        assertFalse(saved.isCurrent());
        assertEquals("Atos", alumni.getCurrentCompany());
        verify(experienceRepository, never()).findByAlumniIdAndCurrentTrue(any());
    }

    @Test
    void uneExperienceCouranteAvecDateDeFinEstRefusee() {
        WorkExperienceRequest req = request("Atos", "Architecte", LocalDate.of(2024, 1, 1))
                .isCurrent(true).endDate(LocalDate.of(2024, 12, 31));

        ApiException ex = assertThrows(ApiException.class, () -> service.addExperience(alumni.getId(), req, owner));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void uneDateDeFinAvantLaDateDeDebutEstRefusee() {
        WorkExperienceRequest req = request("Atos", "Architecte", LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2023, 1, 1));

        ApiException ex = assertThrows(ApiException.class, () -> service.addExperience(alumni.getId(), req, owner));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void unAutreUtilisateurNePeutPasModifierLeParcours() {
        WorkExperienceRequest req = request("Atos", "Architecte", LocalDate.of(2024, 1, 1));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.addExperience(alumni.getId(), req, UUID.randomUUID()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(experienceRepository, never()).save(any());
    }

    @Test
    void alumniInconnuDonne404() {
        UUID unknown = UUID.randomUUID();
        when(alumniRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.addExperience(unknown,
                request("Atos", "Architecte", LocalDate.of(2024, 1, 1)), owner));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertThrows(ApiException.class, () -> service.listExperiences(unknown));
    }
}
