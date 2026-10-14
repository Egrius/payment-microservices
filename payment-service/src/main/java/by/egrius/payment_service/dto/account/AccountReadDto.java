package by.egrius.payment_service.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Response with account information")
public record AccountReadDto(

        @Schema(description = "Account's unique public ID",
                example = "550e8400-e29b-41d4-a716-446655440000")
        UUID publicId,

        @Schema(description = "Account name",
                example = "Main BYN account")
        String name,

        @Schema(description = "Currency code (ISO 4217)",
                example = "BYN")
        String currency,

        @Schema(description = "Current account balance",
                example = "1500.00")
        BigDecimal balance
) {}