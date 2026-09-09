package by.Egrius.auth_server.exception.handler;

import by.Egrius.auth_server.dto.ErrorResponse;
import by.Egrius.auth_server.exception.UserEmailAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@Slf4j
@RestControllerAdvice
public class GlobalControllerExceptionHandler {

    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> onUserEmailAlreadyExistsException(UserEmailAlreadyExistsException e,
                                                                           HttpServletRequest request) {

        log.warn("Email already exists: {}", e.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "Conflict",
                        e.getMessage(),
                        request.getRequestURI()
                ));
    }
}