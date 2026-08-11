package by.egrius.payment_service.exception.payment_service;

import java.util.UUID;

public class SameAccountTransferException extends PaymentServiceException {
    private final UUID accountId;

    public SameAccountTransferException(UUID accountId) {
        super(String.format("Cannot transfer to the same account: %s", accountId));
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}