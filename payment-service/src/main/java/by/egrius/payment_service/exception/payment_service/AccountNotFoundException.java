package by.egrius.payment_service.exception.payment_service;

import by.egrius.payment_service.exception.DeterministicException;
import by.egrius.payment_service.exception.ErrorCode;

import java.util.UUID;

import static by.egrius.payment_service.exception.ErrorCode.ACCOUNT_NOT_FOUND;

public class AccountNotFoundException extends DeterministicException {
    public AccountNotFoundException(String message) {
        super(message);
    }

    public AccountNotFoundException(UUID publicId) {
        super("Account not found with public ID: " + publicId);
    }

    public AccountNotFoundException(Long id) {
        super("Account not found with ID: " + id);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ACCOUNT_NOT_FOUND;
    }
}
