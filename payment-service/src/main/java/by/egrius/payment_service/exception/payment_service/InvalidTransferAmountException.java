package by.egrius.payment_service.exception.payment_service;

import java.math.BigDecimal;

public class InvalidTransferAmountException extends PaymentServiceException {
    private final BigDecimal amount;

    public InvalidTransferAmountException(String message) {
        super(message);
        this.amount = null;
    }

    public InvalidTransferAmountException(BigDecimal amount) {
        super(String.format("Invalid transfer amount: %s. Amount must be positive and non-zero", amount));
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}