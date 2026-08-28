package by.Egrius.notification_service.dto;

public record SubscriptionCreateDto (
        String userId,
        String userEmail,
        String message
) { }
