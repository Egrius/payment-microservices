package by.egrius.payment_service.dto.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCreateDto(
        @NotNull(message = "From account ID cannot be null")
        UUID fromAccountPublicId,

        @NotNull(message = "To account ID cannot be null")
        UUID toAccountPublicId,

        @NotNull(message = "Amount cannot be null")
        @Positive(message = "Amount must be positive")
        BigDecimal amount
) { }
