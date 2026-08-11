package by.egrius.payment_service.dto.transfer;

import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.repository.projection.TransferProjection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferReadDto(
        UUID publicId,
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
