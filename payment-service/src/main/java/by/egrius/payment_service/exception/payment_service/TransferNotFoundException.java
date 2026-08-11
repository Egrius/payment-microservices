package by.egrius.payment_service.exception.payment_service;

import java.util.UUID;

public class TransferNotFoundException extends PaymentServiceException {
    public TransferNotFoundException(String message) {
        super(message);
    }

    public TransferNotFoundException(UUID publicId) {
        super("Transfer not found with public ID: " + publicId);
    }

    public TransferNotFoundException(Long id) {
        super("Transfer not found with ID: " + id);
    }
}