package com.uasz.alumni.analyticsmatching.service;

import static com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.AVAILABLE_FOR_INTERNSHIP;
import static com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.AVAILABLE_FOR_JOB;
import static com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.AVAILABLE_IMMEDIATELY;
import static com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.INTERNSHIP;
import static com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.JOB;
import static com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.NOT_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.MatchScore;
import com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.OfferData;
import com.uasz.alumni.analyticsmatching.service.MatchScoreCalculator.StudentProfileData;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MatchScoreCalculatorTest {

    private static final OfferData BACKEND_JOB = new OfferData(
            List.of("Java", "Spring Boot", "Docker", "Kubernetes"),
            "Développeur Backend Java",
            "Microservices déployés dans le Cloud.",
            JOB);

    @Nested
    class CasDuTicket {

        @Test
        void correspondanceParfaite_vaut100() {
            StudentProfileData student = new StudentProfileData(
                    List.of("java", "spring-boot", "DOCKER", "k8s"), List.of("Cloud", "Microservices"),
                    AVAILABLE_IMMEDIATELY);

            MatchScore result = MatchScoreCalculator.compute(student, BACKEND_JOB);

            assertThat(result.matchScore()).isEqualTo(100);
            assertThat(result.matchingSkills()).containsExactly("Java", "Spring Boot", "Docker", "Kubernetes");
            assertThat(result.missingSkills()).isEmpty();
        }

        @Test
        void aucuneCorrespondance_vaut0() {
            StudentProfileData student = new StudentProfileData(
                    List.of("Photoshop", "Illustrator"), List.of("Design graphique"), NOT_AVAILABLE);

            MatchScore result = MatchScoreCalculator.compute(student, BACKEND_JOB);

            assertThat(result.matchScore()).isZero();
            assertThat(result.matchingSkills()).isEmpty();
            assertThat(result.missingSkills()).containsExactly("Java", "Spring Boot", "Docker", "Kubernetes");
        }

        @Test
        void correspondancePartielle_combineLesTroisCriteres() {
            // compétences 2/4, intérêts 1 retrouvé sur 2 attendus, stage proposé pour un emploi
            StudentProfileData student = new StudentProfileData(
                    List.of("Java", "Docker", "React"), List.of("Cloud", "Design"), AVAILABLE_FOR_INTERNSHIP);

            MatchScore result = MatchScoreCalculator.compute(student, BACKEND_JOB);

            assertThat(result.skillsScore()).isEqualTo(0.5);
            assertThat(result.interestsScore()).isEqualTo(0.5);
            assertThat(result.availabilityScore()).isEqualTo(0.25);
            // 70 × 0,5 + 20 × 0,5 + 10 × 0,25 = 47,5 → 48
            assertThat(result.matchScore()).isEqualTo(48);
            assertThat(result.matchingSkills()).containsExactly("Java", "Docker");
            assertThat(result.missingSkills()).containsExactly("Spring Boot", "Kubernetes");
        }

        @Test
        void informationsAbsentes_neutres() {
            // exemple de docs/matching.md : 70 × 0,25 + 20 × 0,5 + 10 × 0,5 = 32,5 → 33
            StudentProfileData student = new StudentProfileData(List.of("Java"), List.of(), null);

            assertThat(MatchScoreCalculator.compute(student, BACKEND_JOB).matchScore()).isEqualTo(33);
        }
    }

    @Nested
    class Competences {

        @Test
        void normalisation_accentsCasseSeparateursEtAlias() {
            OfferData offer = new OfferData(
                    List.of("Node.js", "JavaScript", "PostgreSQL", "C#", "Génie logiciel"), "Dev", null, JOB);
            StudentProfileData student = new StudentProfileData(
                    List.of("nodejs", "JS", "postgres", "csharp", "GENIE-LOGICIEL"), null, AVAILABLE_FOR_JOB);

            MatchScore result = MatchScoreCalculator.compute(student, offer);

            assertThat(result.skillsScore()).isEqualTo(1.0);
            assertThat(result.missingSkills()).isEmpty();
        }

        @Test
        void cPlusPlusEtCSharpNeSeConfondentPas() {
            OfferData offer = new OfferData(List.of("C++"), "Dev", null, JOB);
            StudentProfileData student = new StudentProfileData(List.of("C#", "C"), null, null);

            assertThat(MatchScoreCalculator.compute(student, offer).missingSkills()).containsExactly("C++");
        }

        @Test
        void competenceExigeeEnDouble_compteUneFois() {
            OfferData offer = new OfferData(List.of("Java", "java ", "Python"), "Dev", null, JOB);
            StudentProfileData student = new StudentProfileData(List.of("Java"), null, null);

            MatchScore result = MatchScoreCalculator.compute(student, offer);

            assertThat(result.skillsScore()).isEqualTo(0.5);
            assertThat(result.matchingSkills()).containsExactly("Java");
        }

        @Test
        void offreSansCompetenceExigee_rienNeManque() {
            OfferData offer = new OfferData(List.of(), "Stage polyvalent", null, INTERNSHIP);
            StudentProfileData student = new StudentProfileData(List.of("Java"), null, null);

            MatchScore result = MatchScoreCalculator.compute(student, offer);

            assertThat(result.skillsScore()).isEqualTo(1.0);
            // 70 + 20 × 0,5 (neutre) + 10 × 0,5 (neutre)
            assertThat(result.matchScore()).isEqualTo(85);
        }

        @Test
        void listesNullesOuLibellesVides_sontIgnores() {
            OfferData offer = new OfferData(Arrays.asList("Java", null, "  "), null, null, null);
            StudentProfileData student = new StudentProfileData(null, null, null);

            MatchScore result = MatchScoreCalculator.compute(student, offer);

            assertThat(result.missingSkills()).containsExactly("Java");
            assertThat(result.skillsScore()).isZero();
        }
    }

    @Nested
    class Interets {

        @Test
        void retrouvesDansLeTitreLaDescriptionOuLesCompetences() {
            OfferData offer = new OfferData(List.of("Docker"), "Ingénieur DevOps",
                    "Projet en intelligence artificielle", JOB);

            assertThat(MatchScoreCalculator.interestsScore(List.of("devops"), offer)).isEqualTo(1.0);
            assertThat(MatchScoreCalculator.interestsScore(List.of("IA"), offer)).isEqualTo(1.0);
            assertThat(MatchScoreCalculator.interestsScore(List.of("docker"), offer)).isEqualTo(1.0);
        }

        @Test
        void motEntierSeulementPourLesClesCourtes() {
            OfferData offer = new OfferData(List.of(), "Analyste Google Analytics", null, JOB);

            assertThat(MatchScoreCalculator.interestsScore(List.of("Go"), offer)).isZero();
        }

        @Test
        void deuxInteretsRetrouvesSuffisentPourLeMaximum() {
            StudentProfileData student = new StudentProfileData(
                    List.of(), List.of("Cloud", "Microservices", "Jeux vidéo", "Photographie"), null);

            assertThat(MatchScoreCalculator.compute(student, BACKEND_JOB).interestsScore()).isEqualTo(1.0);
        }

        @Test
        void aucunInteretDeclare_neutre() {
            assertThat(MatchScoreCalculator.interestsScore(List.of(), BACKEND_JOB)).isEqualTo(0.5);
            assertThat(MatchScoreCalculator.interestsScore(null, BACKEND_JOB)).isEqualTo(0.5);
        }
    }

    @Nested
    class Disponibilite {

        @Test
        void selonLeStatutEtLeTypeDOffre() {
            assertThat(MatchScoreCalculator.availabilityScore(AVAILABLE_IMMEDIATELY, INTERNSHIP)).isEqualTo(1.0);
            assertThat(MatchScoreCalculator.availabilityScore(AVAILABLE_FOR_INTERNSHIP, INTERNSHIP)).isEqualTo(1.0);
            assertThat(MatchScoreCalculator.availabilityScore(AVAILABLE_FOR_INTERNSHIP, JOB)).isEqualTo(0.25);
            assertThat(MatchScoreCalculator.availabilityScore(AVAILABLE_FOR_JOB, JOB)).isEqualTo(1.0);
            assertThat(MatchScoreCalculator.availabilityScore(AVAILABLE_FOR_JOB, INTERNSHIP)).isEqualTo(0.25);
            assertThat(MatchScoreCalculator.availabilityScore(NOT_AVAILABLE, JOB)).isZero();
        }

        @Test
        void inconnue_neutre() {
            assertThat(MatchScoreCalculator.availabilityScore(null, JOB)).isEqualTo(0.5);
            assertThat(MatchScoreCalculator.availabilityScore(AVAILABLE_FOR_JOB, null)).isEqualTo(0.5);
            assertThat(MatchScoreCalculator.availabilityScore("NOUVELLE_VALEUR", JOB)).isEqualTo(0.5);
        }
    }

    @Test
    void argumentsObligatoires() {
        assertThatNullPointerException().isThrownBy(() -> MatchScoreCalculator.compute(null, BACKEND_JOB));
        assertThatNullPointerException().isThrownBy(
                () -> MatchScoreCalculator.compute(new StudentProfileData(null, null, null), null));
    }

    @Test
    void scoreToujoursEntre0Et100() {
        for (String status : Arrays.asList(null, AVAILABLE_IMMEDIATELY, NOT_AVAILABLE)) {
            for (List<String> skills : List.of(List.<String>of(), List.of("Java"), List.of("Java", "Docker",
                    "Spring Boot", "Kubernetes"))) {
                int score = MatchScoreCalculator.compute(
                        new StudentProfileData(skills, List.of("Cloud"), status), BACKEND_JOB).matchScore();
                assertThat(score).isBetween(0, 100);
            }
        }
    }
}
