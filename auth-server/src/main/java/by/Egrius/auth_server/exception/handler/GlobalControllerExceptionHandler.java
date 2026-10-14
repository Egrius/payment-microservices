package by.Egrius.auth_server.exception.handler;

import by.Egrius.auth_server.dto.response.ErrorResponse;
import by.Egrius.auth_server.dto.Violation;
import by.Egrius.auth_server.exception.UserEmailAlreadyExistsException;
import by.Egrius.auth_server.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalControllerExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());

        List<Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new Violation(e.getField(), e.getDefaultMessage(), e.getRejectedValue()))
                .toList();

        return ResponseEntity.badRequest().body(
                ErrorResponse.of(400, "VALIDATION_ERROR", "Validation failed",
                        request.getRequestURI(), violations)
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> onUserNotFoundException(UserNotFoundException e,
                                                                 HttpServletRequest request) {
        log.warn("User was not found: {}", e.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "USER_NOT_FOUND",
                        e.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> onUserEmailAlreadyExistsException(UserEmailAlreadyExistsException e,
                                                                           HttpServletRequest request) {
        log.warn("Email already exists: {}", e.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "USER_EMAIL_EXISTS",
                        e.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "INTERNAL_ERROR",
                        "Internal server error", request.getRequestURI()));
    }
}