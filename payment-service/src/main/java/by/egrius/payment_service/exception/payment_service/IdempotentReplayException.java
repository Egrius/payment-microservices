package by.egrius.payment_service.exception.payment_service;

import by.egrius.payment_service.exception.handler.ErrorResponse;

public class IdempotentReplayException extends RuntimeException {
    private final int httpStatus;
    private final ErrorResponse body;

    public IdempotentReplayException(int httpStatus, ErrorResponse body) {
        super("Idempotent replay: " + httpStatus);
        this.httpStatus = httpStatus;
        this.body = body;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public ErrorResponse getBody() {
        return body;
    }
}