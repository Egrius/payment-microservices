package by.Egrius.notification_service.dto.response;

import java.util.UUID;

public record SubscriptionResponse(
        UUID publicSubscriptionId,
        String message
) { }