package by.egrius.payment_service.dto.transfer;

import by.egrius.payment_service.repository.projection.AdminSenderLeaderboardProjection;

import java.util.UUID;

public record AdminSenderLeaderboardDto(
        UUID userPublicId,
        UUID accountPublicId,
        Long transfersCount
) {
    public static AdminSenderLeaderboardDto fromProjection(AdminSenderLeaderboardProjection p) {
        return new AdminSenderLeaderboardDto(
                p.getUserPublicId(),
                p.getAccountPublicId(),
                p.getTransfersCount()
        );
    }
}