package by.egrius.payment_service.service;

import by.egrius.payment_service.dto.idempotency_key.IdempotencyKeyReadDto;

public record ReserveResult(IdempotencyKeyReadDto keyReadDto, boolean isNew) {}
