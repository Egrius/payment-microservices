package by.egrius.payment_service.dto.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request for transfer creation")
public record TransferCreateDto(

        @Schema(description = "Source account public ID",
                example = "22222222-2222-2222-2222-222222222222")
        @NotNull(message = "From account ID cannot be null")
        UUID fromAccountPublicId,

        @Schema(description = "Destination account public ID",
                example = "33333333-3333-3333-3333-333333333333")
        @NotNull(message = "To account ID cannot be null")
        UUID toAccountPublicId,

        @Schema(description = "Amount to transfer", example = "20.00")
        @NotNull(message = "Amount cannot be null")
        @Positive(message = "Amount must be positive")
        BigDecimal amount
) { }
