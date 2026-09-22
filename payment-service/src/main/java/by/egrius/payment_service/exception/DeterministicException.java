package by.egrius.payment_service.exception;

import by.egrius.payment_service.exception.handler.ErrorResponse;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public abstract class DeterministicException extends RuntimeException {

    public DeterministicException(String message) {
        super(message);
    }

    public DeterministicException() {
        super();
    }

    public abstract ErrorCode getErrorCode();

    public ErrorResponse toErrorResponse() {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(getErrorCode().getHttpStatus())
                .error(HttpStatus.valueOf(getErrorCode().getHttpStatus()).getReasonPhrase())
                .code(getErrorCode().getCode())
                .message(this.getMessage())
                .build();
    }
}
