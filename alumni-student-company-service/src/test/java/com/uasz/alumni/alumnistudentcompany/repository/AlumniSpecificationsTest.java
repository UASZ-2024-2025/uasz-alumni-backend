package com.uasz.alumni.alumnistudentcompany.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Recherche d'Alumni (ALUM-105) exécutée sur une vraie base (H2 en mode PostgreSQL), sans simuler le dépôt. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlumniSpecificationsTest {

    @Autowired
    private AlumniRepository repository;

    @BeforeEach
    void donnees() {
        repository.deleteAll();
        repository.save(alumni("Awa", "Diop", "Informatique", 2022, "Orange", "Développeuse"));
        repository.save(alumni("Moussa", "Ba", "Informatique", 2023, "Sonatel", "Analyste"));
        repository.save(alumni("Fatou", "Sow", "Mathématiques", 2022, "100%_Pur", "Enseignante"));
    }

    @Test
    void sansCritereRenvoieTout() {
        assertThat(noms(AlumniSpecifications.search(null, null, null))).hasSize(3);
    }

    @Test
    void filtreParSpecialiteSansTenirCompteDeLaCasse() {
        assertThat(noms(AlumniSpecifications.search(null, null, " informatique "))).containsExactlyInAnyOrder("Diop", "Ba");
    }

    @Test
    void combineAnneeEtSpecialite() {
        assertThat(noms(AlumniSpecifications.search(null, 2022, "Informatique"))).containsExactly("Diop");
    }

    @Test
    void rechercheDansLeNomCompletLEntrepriseEtLePoste() {
        assertThat(noms(AlumniSpecifications.search("awa d", null, null))).containsExactly("Diop");
        assertThat(noms(AlumniSpecifications.search("sonatel", null, null))).containsExactly("Ba");
        assertThat(noms(AlumniSpecifications.search("enseignante", null, null))).containsExactly("Sow");
    }

    @Test
    void lesJokersLikeSontTraitesCommeDuTexte() {
        assertThat(noms(AlumniSpecifications.search("%", null, null))).containsExactly("Sow");
        assertThat(noms(AlumniSpecifications.search("_", null, null))).containsExactly("Sow");
        assertThat(noms(AlumniSpecifications.search("100%_pur", null, null))).containsExactly("Sow");
    }

    @Test
    void aucunResultat() {
        assertThat(noms(AlumniSpecifications.search("inconnu", null, null))).isEmpty();
    }

    private List<String> noms(org.springframework.data.jpa.domain.Specification<Alumni> spec) {
        return repository.findAll(spec).stream().map(Alumni::getLastName).toList();
    }

    private static Alumni alumni(String prenom, String nom, String specialite, int annee, String entreprise, String poste) {
        Alumni a = Alumni.provisioned(UUID.randomUUID(), prenom, nom, nom.toLowerCase() + "@uasz.sn");
        a.updateProfile(annee, specialite, null);
        a.updateCurrentJob(entreprise, poste);
        return a;
    }
}
