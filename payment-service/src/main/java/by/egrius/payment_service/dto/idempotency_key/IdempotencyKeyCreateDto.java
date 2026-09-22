package by.egrius.payment_service.dto.idempotency_key;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public record IdempotencyKeyCreateDto(
        UUID value,
        UUID userId,
        String requestHash,
        LocalDateTime createdAt,
        long ttlAmount,
        TimeUnit ttlTimeUnit
) { }
