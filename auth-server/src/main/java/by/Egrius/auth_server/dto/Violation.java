package by.Egrius.auth_server.dto;

public record Violation(
        String field,
        String message,
        Object rejectedValue
) {}