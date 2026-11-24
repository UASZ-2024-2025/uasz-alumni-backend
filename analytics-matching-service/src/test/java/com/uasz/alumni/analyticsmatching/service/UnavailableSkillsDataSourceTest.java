package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.uasz.alumni.analyticsmatching.exceptions.NotImplementedYetException;

/** Hors profils local/dev, le rapport doit répondre 501 au lieu de renvoyer des données d'exemple. */
class UnavailableSkillsDataSourceTest {

    private final UnavailableSkillsDataSource source = new UnavailableSkillsDataSource();

    @Test
    void offresIndisponibles_leve501() {
        assertThatThrownBy(source::skillsRequiredByOffers)
                .isInstanceOf(NotImplementedYetException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void profilsIndisponibles_leve501() {
        assertThatThrownBy(source::skillsOfStudentsAndAlumni)
                .isInstanceOf(NotImplementedYetException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    }
}
