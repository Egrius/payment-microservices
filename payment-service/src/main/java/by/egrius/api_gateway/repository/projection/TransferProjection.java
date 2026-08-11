package by.egrius.api_gateway.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface TransferProjection {
    UUID getPublicId();
    UUID getFromAccountId();
    UUID getToAccountId();
    BigDecimal getAmount();
    String getStatus();
    LocalDateTime getCreatedAt();
    LocalDateTime getProcessedAt();
    String getReason();
}