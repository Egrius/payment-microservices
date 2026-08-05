package by.egrius.api_gateway.dto.transfer;

import by.egrius.api_gateway.entity.TransferStatus;
import by.egrius.api_gateway.repository.projection.TransferProjection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferReadDto(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        TransferStatus status,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        String reason
) {
    public static TransferReadDto fromProjection(TransferProjection projection) {
        return new TransferReadDto(
                projection.getPublicId(),
                projection.getFromAccountId(),
                projection.getToAccountId(),
                projection.getAmount(),
                TransferStatus.valueOf(projection.getStatus()),
                projection.getCreatedAt(),
                projection.getProcessedAt(),
                projection.getReason()
        );
    }
}
