package com.uasz.alumni.alumnistudentcompany.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.model.PromotionEntity;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.PromotionRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.Promotion;
import com.uasz.alumni.alumnistudentcompany.spi.model.PromotionCreateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class PromotionServiceTest {

    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final AlumniRepository alumni = mock(AlumniRepository.class);
    private final PromotionService service = new PromotionService(promotions, alumni);

    private static PromotionCreateRequest request(Integer year, String specialty) {
        return new PromotionCreateRequest().academicYear(year).specialty(specialty);
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((ApiException) e).getStatus();
    }

    @Test
    void creeUnePromotionAvecSpecialiteNettoyeeEtGraduateCountCalcule() {
        when(promotions.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(alumni.countByGraduationYearAndSpecialtyIgnoreCase(2025, "Master Génie Logiciel")).thenReturn(3L);

        Promotion created = service.create(request(2025, "  Master Génie Logiciel "));

        ArgumentCaptor<PromotionEntity> saved = ArgumentCaptor.forClass(PromotionEntity.class);
        verify(promotions).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAcademicYear()).isEqualTo(2025);
        assertThat(saved.getValue().getSpecialty()).isEqualTo("Master Génie Logiciel");
        assertThat(created.getSpecialty()).isEqualTo("Master Génie Logiciel");
        assertThat(created.getGraduateCount()).isEqualTo(3);
    }

    @Test
    void refuseUneAnneeHorsLimites() {
        for (Integer year : new Integer[] {null, 1989, 2101}) {
            assertThatThrownBy(() -> service.create(request(year, "Réseaux")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(promotions, never()).saveAndFlush(any());
    }

    @Test
    void refuseUneSpecialiteVideOuTropLongue() {
        for (String specialty : new String[] {null, "   ", "x".repeat(151)}) {
            assertThatThrownBy(() -> service.create(request(2025, specialty)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(promotions, never()).saveAndFlush(any());
    }

    @Test
    void refuseUnDoublonAnneeSpecialiteSansTenirCompteDeLaCasse() {
        when(promotions.existsByAcademicYearAndSpecialtyIgnoreCase(2025, "master génie logiciel")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(2025, "master génie logiciel")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        verify(promotions, never()).saveAndFlush(any());
    }

    @Test
    void deuxCreationsSimultaneesDonnent409GraceALIndexUnique() {
        when(promotions.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_promotions_year_specialty"));

        assertThatThrownBy(() -> service.create(request(2025, "Réseaux")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void listeLesPromotionsAvecLeurNombreDeDiplomes() {
        when(promotions.findAllByOrderByAcademicYearDescSpecialtyAsc()).thenReturn(List.of(
                new PromotionEntity(2025, "Génie Logiciel"), new PromotionEntity(2024, "Réseaux")));
        when(alumni.countByGraduationYearAndSpecialtyIgnoreCase(2025, "Génie Logiciel")).thenReturn(2L);
        when(alumni.countByGraduationYearAndSpecialtyIgnoreCase(2024, "Réseaux")).thenReturn(0L);

        List<Promotion> list = service.list();

        assertThat(list).extracting(Promotion::getAcademicYear).containsExactly(2025, 2024);
        assertThat(list).extracting(Promotion::getGraduateCount).containsExactly(2, 0);
    }

    @Test
    void lesDiplomesSontLesAlumniDeMemeAnneeEtSpecialiteEtLeurNombreEgaleGraduateCount() {
        UUID id = UUID.randomUUID();
        PromotionEntity promo = new PromotionEntity(2025, "Génie Logiciel");
        Alumni awa = Alumni.provisioned(UUID.randomUUID(), "Awa", "Diop", "awa@uasz.sn");
        awa.updateProfile(2025, "génie logiciel", null);
        Alumni moussa = Alumni.provisioned(UUID.randomUUID(), "Moussa", "Ndiaye", "moussa@uasz.sn");
        moussa.updateProfile(2025, "Génie Logiciel", null);
        when(promotions.findById(id)).thenReturn(Optional.of(promo));
        when(alumni.findByGraduationYearAndSpecialtyIgnoreCaseOrderByLastNameAscFirstNameAsc(2025, "Génie Logiciel"))
                .thenReturn(List.of(awa, moussa));
        when(alumni.countByGraduationYearAndSpecialtyIgnoreCase(2025, "Génie Logiciel")).thenReturn(2L);

        List<StudentProfile> graduates = service.graduates(id);

        assertThat(graduates).extracting(StudentProfile::getLastName).containsExactly("Diop", "Ndiaye");
        assertThat(graduates.get(0).getUserId()).isEqualTo(awa.getUserId());
        assertThat(graduates.get(0).getEmail()).isEqualTo("awa@uasz.sn");
        assertThat(graduates.get(0).getAcademicYear()).isEqualTo(2025);
        when(promotions.findAllByOrderByAcademicYearDescSpecialtyAsc()).thenReturn(List.of(promo));
        assertThat(service.list().get(0).getGraduateCount()).isEqualTo(graduates.size());
    }

    @Test
    void diplomesDUnePromotionInconnueDonne404() {
        when(promotions.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.graduates(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
