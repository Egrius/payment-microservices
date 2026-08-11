package by.egrius.payment_service.repository.projection;

import java.util.UUID;

public interface AdminSenderLeaderboardProjection {
    UUID getUserId();
    UUID getAccountId();
    Long getTransfersCount();
}