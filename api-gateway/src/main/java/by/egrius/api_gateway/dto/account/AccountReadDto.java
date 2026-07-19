package by.egrius.api_gateway.dto.account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountReadDto(
        UUID publicId,
        String name,
        String currency,
        BigDecimal balance
) { }
