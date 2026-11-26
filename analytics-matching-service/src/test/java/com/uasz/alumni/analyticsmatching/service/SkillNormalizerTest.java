package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SkillNormalizerTest {

    @ParameterizedTest(name = "« {0} » → {1}")
    @CsvSource({
            "Java, java",
            "'  JAVA  ', java",
            "Spring Boot, springboot",
            "spring-boot, springboot",
            "SpringBoot, springboot",
            "Node.js, nodejs",
            "node, nodejs",
            "JS, javascript",
            "k8s, kubernetes",
            "Postgres, postgresql",
            "C++, c++",
            "C#, c#",
            "csharp, c#",
            "Génie Logiciel, genielogiciel",
            "IA, intelligenceartificielle",
            "'', ''",
    })
    void key(String raw, String expected) {
        assertThat(SkillNormalizer.key(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "« {0} » → « {1} »")
    @CsvSource({
            "Intelligence  Artificielle, intelligence artificielle",
            "Développement_Web/Mobile, developpement web mobile",
            "'  Réseaux  ', reseaux",
    })
    void text(String raw, String expected) {
        assertThat(SkillNormalizer.text(raw)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void nullDonneUneChaineVide() {
        assertThat(SkillNormalizer.key(null)).isEmpty();
        assertThat(SkillNormalizer.text(null)).isEmpty();
    }
}
