package by.Egrius.auth_server.dto.response;

import by.Egrius.auth_server.dto.Violation;

import java.time.LocalDateTime;
import java.util.List;

public record ValidationErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        List<Violation> violations
) {}