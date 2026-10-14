package by.egrius.payment_service.dto.transfer;

import by.egrius.payment_service.repository.projection.AdminSenderLeaderboardProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Leaderboard entry — top sender by transfer count")
public record AdminSenderLeaderboardDto(
        @Schema(description = "Public ID of the user",
                example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userPublicId,

        @Schema(description = "Public ID of the account",
                example = "660e8400-e29b-41d4-a716-446655440001")
        UUID accountPublicId,

        @Schema(description = "Number of transfers sent in the period",
                example = "42")
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