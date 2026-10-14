package by.egrius.payment_service.dto.transfer;

import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.repository.projection.TransferProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Response with transfer information")
public record TransferReadDto(

        @Schema(description = "Transfer's unique public ID",
                example = "550e8400-e29b-41d4-a716-446655440000")
        UUID publicId,

        @Schema(description = "Public ID of the source account",
                example = "660e8400-e29b-41d4-a716-446655440001")
        UUID fromAccountPublicId,

        @Schema(description = "Public ID of the destination account",
                example = "770e8400-e29b-41d4-a716-446655440002")
        UUID toAccountPublicId,

        @Schema(description = "Transfer amount", example = "100.00")
        BigDecimal amount,

        @Schema(description = "Current transfer status",
                example = "COMPLETED",
                allowableValues = {"PENDING", "COMPLETED", "FAILED"})
        TransferStatus status,

        @Schema(description = "Timestamp when the transfer was created",
                example = "2026-10-12T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "Timestamp when the transfer was processed (null while PENDING)",
                example = "2026-10-12T10:15:35")
        LocalDateTime processedAt,

        @Schema(description = "Reason of failure (null unless status is FAILED)",
                example = "Insufficient funds")
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
