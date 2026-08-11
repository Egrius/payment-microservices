package by.egrius.payment_service.dto.transfer;

import by.egrius.payment_service.repository.projection.AdminSenderLeaderboardProjection;

import java.util.UUID;

public record AdminSenderLeaderboardDto(
        UUID userId,
        UUID accountId,
        Long transfersCount
) {
    public static AdminSenderLeaderboardDto fromProjection(AdminSenderLeaderboardProjection p) {
        return new AdminSenderLeaderboardDto(
                p.getUserId(),
                p.getAccountId(),
                p.getTransfersCount()
        );
    }
}