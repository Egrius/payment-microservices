package by.egrius.payment_service.exception.payment_service;

public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String message) {
        super(message + " is invalid property to sort");
    }
}
