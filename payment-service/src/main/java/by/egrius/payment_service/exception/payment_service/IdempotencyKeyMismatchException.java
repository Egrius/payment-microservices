package by.egrius.payment_service.exception.payment_service;

import by.egrius.payment_service.exception.DeterministicException;
import by.egrius.payment_service.exception.ErrorCode;


public class IdempotencyKeyMismatchException extends DeterministicException {

    public IdempotencyKeyMismatchException(String message) {
        super(message);
    }

    public IdempotencyKeyMismatchException() {
        super();
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.IDEMPOTENCY_KEY_MISMATCH;
    }

}
