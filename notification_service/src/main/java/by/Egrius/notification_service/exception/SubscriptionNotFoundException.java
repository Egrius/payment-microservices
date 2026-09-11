package by.Egrius.notification_service.exception;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(String userId) {
        super(String.format("No active subscription found for user: %s", userId));
    }
}