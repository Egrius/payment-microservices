package by.egrius.payment_service.exception.payment_service;

public class TransferProcessingException extends PaymentServiceException {
    private final Long transferId;

    public TransferProcessingException(Long transferId, String message) {
        super(String.format("Failed to process transfer %d: %s", transferId, message));
        this.transferId = transferId;
    }

    public TransferProcessingException(Long transferId, String message, Throwable cause) {
        super(String.format("Failed to process transfer %d: %s", transferId, message), cause);
        this.transferId = transferId;
    }

    public Long getTransferId() {
        return transferId;
    }
}