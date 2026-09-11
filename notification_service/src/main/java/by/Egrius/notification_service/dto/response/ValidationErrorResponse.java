package by.Egrius.notification_service.dto.response;

import by.Egrius.notification_service.dto.Violation;

import java.time.LocalDateTime;
import java.util.List;

public record ValidationErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        List<Violation> violations
) {}