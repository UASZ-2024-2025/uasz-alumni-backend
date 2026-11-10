package com.uasz.alumni.user.exceptions;

import com.uasz.alumni.user.spi.model.ErrorResponse;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Toutes les erreurs sont renvoyées au format ErrorResponse du contrat (code, message, timestamp).
 * Les erreurs Spring MVC (validation, JSON illisible, 404, 405...) passent par handleExceptionInternal.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(build(ex.getCode(), ex.getMessage()));
    }

    /** Laisse Spring Security produire les 401/403. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurity(RuntimeException ex) {
        throw ex;
    }

    /** Validation Bean Validation déclenchée par @Validated : 400 lisible, sans nom de classe Java. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::describe)
                .sorted()
                .collect(Collectors.joining(" ; "));
        return ResponseEntity.badRequest().body(build(VALIDATION_ERROR, validationMessage(details)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Erreur inattendue", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(build("INTERNAL_ERROR", "Erreur interne du serveur"));
    }

    /**
     * Le message renvoyé au client ne contient jamais le texte brut d'une exception (il peut citer des
     * classes Java ou des détails d'analyse JSON) : uniquement un code et une phrase lisible.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String code;
        String message;
        if (ex instanceof MethodArgumentNotValidException invalid) {
            code = VALIDATION_ERROR;
            message = validationMessage(invalid.getBindingResult().getAllErrors().stream()
                    .map(GlobalExceptionHandler::describe)
                    .sorted()
                    .collect(Collectors.joining(" ; ")));
        } else if (ex instanceof HandlerMethodValidationException invalid) {
            code = VALIDATION_ERROR;
            message = validationMessage(invalid.getAllErrors().stream()
                    .map(GlobalExceptionHandler::describe)
                    .sorted()
                    .collect(Collectors.joining(" ; ")));
        } else if (ex instanceof HttpMessageNotReadableException) {
            code = MALFORMED_REQUEST;
            message = "Corps de la requête illisible ou invalide (JSON mal formé ou valeur non reconnue).";
        } else {
            HttpStatus resolved = HttpStatus.resolve(statusCode.value());
            code = resolved != null ? resolved.name() : "HTTP_" + statusCode.value();
            message = (body instanceof ProblemDetail pd && pd.getDetail() != null)
                    ? pd.getDetail()
                    : (resolved != null ? resolved.getReasonPhrase() : "Erreur");
        }
        return ResponseEntity.status(statusCode).headers(headers).body(build(code, message));
    }

    private static String validationMessage(String details) {
        return details.isEmpty() ? "Données invalides." : "Données invalides : " + details;
    }

    private static String describe(MessageSourceResolvable error) {
        String text = error.getDefaultMessage() == null ? "valeur invalide" : error.getDefaultMessage();
        return error instanceof FieldError field ? field.getField() + " : " + text : text;
    }

    private static String describe(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.substring(path.lastIndexOf('.') + 1);
        return field + " : " + violation.getMessage();
    }

    private static ErrorResponse build(String code, String message) {
        ErrorResponse error = new ErrorResponse();
        error.setCode(code);
        error.setMessage(message);
        error.setTimestamp(OffsetDateTime.now());
        return error;
    }
}
