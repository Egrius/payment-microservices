package by.Egrius.notification_service.exception;

public class SubscriptionAlreadyExistsException extends RuntimeException {
    public SubscriptionAlreadyExistsException(String userId) {
        super(String.format("Active subscription already exists for user: %s", userId));
    }
}
