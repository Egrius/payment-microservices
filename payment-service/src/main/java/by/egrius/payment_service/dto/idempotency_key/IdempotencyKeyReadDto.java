package by.egrius.payment_service.dto.idempotency_key;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.exception.handler.ErrorResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public record IdempotencyKeyReadDto(
        UUID value,
        UUID userId,
        String requestHash,
        Integer responseStatus,
        TransferReadDto responseBody,
        ErrorResponse errorResponse,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) { }
