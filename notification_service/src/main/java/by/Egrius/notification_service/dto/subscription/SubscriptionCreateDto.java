package by.Egrius.notification_service.dto.subscription;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubscriptionCreateDto (
        String userId,
        @NotBlank @Email @Size(max = 255) String userEmail,
        @NotBlank @Size(min = 1, max = 500) String message
) { }
