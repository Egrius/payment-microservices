package by.egrius.api_gateway.dto.transfer;

import by.egrius.api_gateway.repository.projection.AdminSenderLeaderboardProjection;

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