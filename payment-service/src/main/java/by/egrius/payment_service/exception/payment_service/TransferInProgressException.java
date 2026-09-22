package by.egrius.payment_service.exception.payment_service;

import by.egrius.payment_service.exception.DeterministicException;
import by.egrius.payment_service.exception.ErrorCode;

public class TransferInProgressException extends DeterministicException {

    public TransferInProgressException(String message) {
        super(message);
    }

    public TransferInProgressException() {
        super();
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.TRANSFER_IN_PROGRESS;
    }

}