package com.uasz.alumni.alumnistudentcompany.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import com.uasz.alumni.alumnistudentcompany.model.Experience;
import com.uasz.alumni.alumnistudentcompany.repository.AlumniRepository;
import com.uasz.alumni.alumnistudentcompany.repository.ExperienceRepository;
import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniEmploymentRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Situation d'emploi des diplômés (ALUM-183) sur une vraie base (H2 en mode PostgreSQL). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlumniEmploymentServiceTest {

    @Autowired AlumniEmploymentService service;
    @Autowired AlumniRepository alumni;
    @Autowired ExperienceRepository experiences;

    @BeforeEach
    void jeuDeTest() {
        experiences.deleteAll();
        alumni.deleteAll();
        Alumni a1 = diplome("Awa", 2024, "Génie Logiciel");
        Alumni a2 = diplome("Moussa", 2024, "Génie Logiciel");
        diplome("Fatou", 2024, "Génie Logiciel");                 // sans expérience
        Alumni a4 = diplome("Ibou", 2024, " génie logiciel ");    // même spécialité, casse et espaces différents
        Alumni a5 = diplome("Khady", 2023, "Réseaux");
        Alumni a6 = diplome("Omar", 2023, "Réseaux");
        diplome("Sokhna", 2023, "Cybersécurité");
        diplome("Sans-Specialite", 2024, null);                   // ignoré : spécialité absente
        diplome("Sans-Annee", null, "Réseaux");                   // ignoré : année absente
        diplome("Blanc", 2024, "   ");                            // ignoré : spécialité vide

        experience(a1, LocalDate.of(2024, 9, 15), null, true);
        experience(a2, LocalDate.of(2024, 10, 1), null, true);
        experience(a4, LocalDate.of(2025, 1, 1), null, true);
        experience(a5, LocalDate.of(2023, 3, 1), null, true);
        experience(a6, LocalDate.of(2023, 8, 1), LocalDate.of(2023, 12, 31), false); // passée : pas en emploi
        experience(a1, LocalDate.of(2023, 6, 1), LocalDate.of(2023, 8, 31), false);   // stage antérieur
    }

    @Test
    void renvoieUneLigneParDiplomeTrieeSansDonneePersonnelle() {
        List<AlumniEmploymentRecord> records = service.list(null, null);

        assertThat(records).extracting(AlumniEmploymentRecord::getGraduationYear)
                .containsExactly(2024, 2024, 2024, 2024, 2023, 2023, 2023);
        assertThat(records).extracting(r -> r.getSpecialty().toLowerCase())
                .containsExactly("génie logiciel", "génie logiciel", "génie logiciel", "génie logiciel",
                        "cybersécurité", "réseaux", "réseaux");
        assertThat(records).extracting(AlumniEmploymentRecord::getEmployed)
                .filteredOn(Boolean::booleanValue).hasSize(4);
    }

    @Test
    void lEtatEmploiVientDeLExperienceEnCoursEtLesDatesSontTriees() {
        List<AlumniEmploymentRecord> records = service.list(2024, "Génie Logiciel");

        assertThat(records).hasSize(4);
        assertThat(records).extracting(AlumniEmploymentRecord::getEmployed)
                .containsExactlyInAnyOrder(true, true, true, false);
        assertThat(records).filteredOn(r -> r.getExperienceStartDates().size() == 2).singleElement()
                .satisfies(r -> assertThat(r.getExperienceStartDates())
                        .containsExactly(LocalDate.of(2023, 6, 1), LocalDate.of(2024, 9, 15)));
    }

    @Test
    void uneExperiencePasseeNeFaitPasDUnDiplomeUnDiplomeEnEmploi() {
        assertThat(service.list(2023, "réseaux"))
                .extracting(AlumniEmploymentRecord::getEmployed, r -> r.getExperienceStartDates().getFirst())
                .containsExactlyInAnyOrder(
                        tuple(true, LocalDate.of(2023, 3, 1)),
                        tuple(false, LocalDate.of(2023, 8, 1)));
    }

    @Test
    void lesFiltresSeCumulentEtUnFiltreSansResultatDonneUneListeVide() {
        assertThat(service.list(2023, null)).hasSize(3);
        assertThat(service.list(null, "  RÉSEAUX  ")).hasSize(2);
        assertThat(service.list(2024, "Réseaux")).isEmpty();
        assertThat(service.list(1999, null)).isEmpty();
    }

    private Alumni diplome(String prenom, Integer annee, String specialite) {
        Alumni a = Alumni.provisioned(UUID.randomUUID(), prenom, "Test", prenom.toLowerCase() + "@uasz.sn");
        a.updateProfile(annee, specialite, null);
        return alumni.save(a);
    }

    private void experience(Alumni a, LocalDate debut, LocalDate fin, boolean enCours) {
        experiences.save(new Experience(a.getId(), "Entreprise", "Poste", debut, fin, enCours, null));
    }
}
