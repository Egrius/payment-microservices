package by.egrius.payment_service.repository.projection;

import java.util.UUID;

public interface AdminSenderLeaderboardProjection {
    UUID getUserPublicId();
    UUID getAccountPublicId();
    Long getTransfersCount();
}