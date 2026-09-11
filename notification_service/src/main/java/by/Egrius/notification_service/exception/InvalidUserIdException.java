package by.Egrius.notification_service.exception;

public class InvalidUserIdException extends RuntimeException {
    public InvalidUserIdException(String userId) {
        super(String.format("Invalid user ID: %s", userId));
    }
}