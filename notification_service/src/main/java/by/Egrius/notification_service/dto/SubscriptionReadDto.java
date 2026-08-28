package by.Egrius.notification_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubscriptionReadDto (
        UUID publicId,
        UUID publicUserId,
        String userEmail,
        String message,
        boolean isActive,
        LocalDateTime createdAt
) { }
