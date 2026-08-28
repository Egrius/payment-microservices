package by.Egrius.notification_service.dto;

import java.util.UUID;

public record SubscriptionResponse(
        UUID publicSubscriptionId,
        String message
) { }