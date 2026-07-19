package by.egrius.api_gateway.dto.transfer;

import by.egrius.api_gateway.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferReadDto(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        TransferStatus status,
        LocalDateTime createdAt
) {
}
