package by.egrius.payment_service.exception.payment_service;

import java.util.UUID;

public class AccountAccessDeniedException extends PaymentServiceException {
    private final UUID accountId;
    private final UUID userId;

    public AccountAccessDeniedException(UUID accountId, UUID userId) {
        super(String.format("User %s does not have access to account %s", userId, accountId));
        this.accountId = accountId;
        this.userId = userId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getUserId() {
        return userId;
    }
}