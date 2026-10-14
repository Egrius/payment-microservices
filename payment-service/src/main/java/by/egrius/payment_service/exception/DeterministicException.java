package by.egrius.payment_service.exception;

import by.egrius.payment_service.dto.response.ErrorResponse;
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

    /*
    This method builds a 'logic' exception without path.
    It's used for idempotency logic only.
    Use GlobalExceptionHandler.buildErrorResponse for http responses.
     */
    public ErrorResponse toErrorResponse() {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(getErrorCode().getHttpStatus())
                .code(getErrorCode().getCode())
                .message(this.getMessage())
                .build();
    }
}
