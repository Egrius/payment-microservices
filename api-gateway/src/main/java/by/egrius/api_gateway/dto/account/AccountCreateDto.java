package by.egrius.api_gateway.dto.account;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.Length;

public record AccountCreateDto(
        @NotEmpty
        String name,

        @NotEmpty
        @Length(min = 2, max = 3)
        String currency
) { }
