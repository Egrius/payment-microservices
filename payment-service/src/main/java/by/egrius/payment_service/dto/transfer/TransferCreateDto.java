package by.egrius.payment_service.dto.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request for transfer creation")
public record TransferCreateDto(

        @Schema(description = "Public ID of the source account",
                example = "660e8400-e29b-41d4-a716-446655440001")
        @NotNull(message = "From account ID cannot be null")
        UUID fromAccountPublicId,

        @Schema(description = "Public ID of the destination account",
                example = "770e8400-e29b-41d4-a716-446655440002")
        @NotNull(message = "To account ID cannot be null")
        UUID toAccountPublicId,

        @Schema(description = "Transfer amount, must be positive",
                example = "100.00")
        @NotNull(message = "Amount cannot be null")
        @Positive(message = "Amount must be positive")
        BigDecimal amount
) { }
