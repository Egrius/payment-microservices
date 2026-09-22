package by.egrius.payment_service.exception;

public enum ErrorCode {
    SAME_ACCOUNT(400, "SELF_TRANSFER"),
    ACCOUNT_NOT_FOUND(404, "ACCOUNT_NOT_FOUND"),
    IDEMPOTENCY_KEY_MISMATCH(422, "IDEMPOTENCY_KEY_MISMATCH"),
    TRANSFER_IN_PROGRESS(409, "TRANSFER_IN_PROGRESS");

    private final int httpStatus;
    private final String code;

    ErrorCode(int httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
}
