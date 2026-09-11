package by.Egrius.notification_service.dto;

public record Violation(
        String field,
        String message,
        Object rejectedValue
) {}