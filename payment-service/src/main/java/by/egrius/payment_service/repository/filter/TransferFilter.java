package by.egrius.payment_service.repository.filter;

import by.egrius.payment_service.entity.TransferStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Filters for transfer list")
public record TransferFilter (
        @Schema(description = "Filter by status", example = "COMPLETED")
        TransferStatus status,

        @Schema(description = "Created after (ISO date)", example = "2026-01-01T00:00:00")
        LocalDateTime fromDate,

        @Schema(description = "Created before (ISO date)", example = "2026-12-31T23:59:59")
        LocalDateTime toDate,

        @Schema(description = "Minimum amount (inclusive)", example = "10.00")
        BigDecimal minAmount,

        @Schema(description = "Maximum amount (inclusive)", example = "1000.00")
        BigDecimal maxAmount
) {}
