package com.uasz.alumni.user.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uasz.alumni.user.spi.model.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void constraintViolationBecomesReadable400WithFieldName() {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("registerUser.registerRequest.email");
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("doit être une adresse e-mail bien formée");

        ResponseEntity<ErrorResponse> response =
                handler.handleConstraintViolation(new ConstraintViolationException("brut", Set.of(violation)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage())
                .contains("email : doit être une adresse e-mail bien formée")
                .doesNotContain("registerUser");
    }

    @Test
    void constraintViolationWithoutDetailsStillGivesAMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleConstraintViolation(new ConstraintViolationException("brut", Set.of()));

        assertThat(response.getBody().getMessage()).isEqualTo("Données invalides.");
    }

    @Test
    void apiExceptionKeepsItsStatusAndBusinessCode() {
        ResponseEntity<ErrorResponse> response = handler.handleApiException(new EmailAlreadyUsedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("EMAIL_ALREADY_USED");
    }
}
