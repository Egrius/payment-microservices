package by.egrius.api_gateway.repository.projection;

import java.util.UUID;

public interface AdminSenderLeaderboardProjection {
    UUID getUserId();
    UUID getAccountId();
    Long getTransfersCount();
}