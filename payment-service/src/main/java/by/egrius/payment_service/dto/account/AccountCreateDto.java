package by.egrius.payment_service.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.Length;

public record AccountCreateDto(
        @Schema(description = "Account name", example = "Main account")
        @NotEmpty
        String name,

        @Schema(description = "Currency", example = "BYN")
        @NotEmpty
        @Length(min = 2, max = 3)
        String currency
) { }
