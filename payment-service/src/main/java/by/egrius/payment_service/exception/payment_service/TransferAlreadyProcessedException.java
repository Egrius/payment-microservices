package by.egrius.payment_service.exception.payment_service;

import by.egrius.payment_service.entity.TransferStatus;

public class TransferAlreadyProcessedException extends PaymentServiceException {
    private final Long transferId;
    private final TransferStatus currentStatus;

    public TransferAlreadyProcessedException(Long transferId, TransferStatus currentStatus) {
        super(String.format("Transfer %d already processed with status: %s", transferId, currentStatus));
        this.transferId = transferId;
        this.currentStatus = currentStatus;
    }

    public Long getTransferId() {
        return transferId;
    }

    public TransferStatus getCurrentStatus() {
        return currentStatus;
    }
}