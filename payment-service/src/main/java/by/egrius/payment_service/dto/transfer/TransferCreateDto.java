package by.egrius.payment_service.dto.transfer;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCreateDto(
        UUID fromAccountPublicId,
        UUID toAccountPublicId,
        BigDecimal amount
) { }
