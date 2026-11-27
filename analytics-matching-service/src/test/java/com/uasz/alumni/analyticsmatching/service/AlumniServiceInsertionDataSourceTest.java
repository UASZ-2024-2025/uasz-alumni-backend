package com.uasz.alumni.analyticsmatching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uasz.alumni.alumnistudentcompany.spi.model.AlumniEmploymentRecord;
import com.uasz.alumni.analyticsmatching.exceptions.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class AlumniServiceInsertionDataSourceTest {

    private final AlumniEmploymentClient client = mock(AlumniEmploymentClient.class);
    private final AlumniServiceInsertionDataSource source = new AlumniServiceInsertionDataSource(client);

    @Test
    void convertitLaReponseDuServiceAlumni() {
        when(client.list(2024, "Génie Logiciel")).thenReturn(List.of(new AlumniEmploymentRecord()
                .graduationYear(2024).specialty("Génie Logiciel").employed(true)
                .experienceStartDates(List.of(LocalDate.of(2024, 9, 15)))));

        assertThat(source.graduates(2024, "Génie Logiciel")).containsExactly(
                new GraduateEmployment(2024, "Génie Logiciel", true, List.of(LocalDate.of(2024, 9, 15))));
    }

    @Test
    void ignoreLesElementsSansAnneeNiSpecialite() {
        when(client.list(any(), any())).thenReturn(List.of(
                new AlumniEmploymentRecord().specialty("Réseaux").employed(true),
                new AlumniEmploymentRecord().graduationYear(2024).employed(true)));

        assertThat(source.graduates(null, null)).isEmpty();
    }

    @Test
    void unServiceAlumniInjoignableDonne503() {
        when(client.list(any(), any())).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> source.graduates(null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("ALUMNI_SERVICE_UNAVAILABLE");
                });
    }

    @Test
    void uneErreurServeurDuServiceAlumniDonne503() {
        when(client.list(any(), any())).thenThrow(HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "bad gateway", null, new byte[0], null));

        assertThatThrownBy(() -> source.graduates(null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
