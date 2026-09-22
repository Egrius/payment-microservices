package by.egrius.payment_service.exception.payment_service;

import by.egrius.payment_service.exception.DeterministicException;
import by.egrius.payment_service.exception.ErrorCode;

import java.util.UUID;

public class SameAccountTransferException extends DeterministicException {
    private final UUID accountId;

    public SameAccountTransferException(UUID accountId) {
        super(String.format("Cannot transfer to the same account: %s", accountId));
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.SAME_ACCOUNT;
    }
}