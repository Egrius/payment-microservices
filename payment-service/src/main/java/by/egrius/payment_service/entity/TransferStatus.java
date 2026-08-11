package by.egrius.payment_service.entity;

public enum TransferStatus {
    PENDING,    // In a queue, awaits for processing
    COMPLETED,  // Successfully completed
    FAILED      // Error (not enough money and so on)
}