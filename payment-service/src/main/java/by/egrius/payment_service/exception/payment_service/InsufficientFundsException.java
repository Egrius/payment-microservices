package by.egrius.payment_service.exception.payment_service;

import java.math.BigDecimal;

public class InsufficientFundsException extends PaymentServiceException {
    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientFundsException(String message) {
        super(message);
        this.available = null;
        this.requested = null;
    }

    public InsufficientFundsException(BigDecimal available, BigDecimal requested) {
        super(String.format("Insufficient funds: available %s, requested %s", available, requested));
        this.available = available;
        this.requested = requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getRequested() {
        return requested;
    }
}