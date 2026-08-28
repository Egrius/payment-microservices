package by.egrius.payment_service.exception.cache;

public class CacheTypeMismatchException extends RuntimeException {
    public CacheTypeMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}