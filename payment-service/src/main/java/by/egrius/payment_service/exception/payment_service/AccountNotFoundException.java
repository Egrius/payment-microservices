package by.egrius.payment_service.exception.payment_service;

import java.util.UUID;

public class AccountNotFoundException extends PaymentServiceException {
    public AccountNotFoundException(String message) {
        super(message);
    }

    public AccountNotFoundException(UUID publicId) {
        super("Account not found with public ID: " + publicId);
    }

    public AccountNotFoundException(Long id) {
        super("Account not found with ID: " + id);
    }
}
