package by.egrius.payment_service.exception.payment_service;

import java.util.UUID;

public class TransferUpdateException extends RuntimeException {
    public TransferUpdateException(UUID transferId) {
        super("Failed to update transfer status for id: " + transferId);
    }
}