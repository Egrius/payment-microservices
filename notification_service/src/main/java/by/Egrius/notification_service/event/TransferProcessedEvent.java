package by.Egrius.notification_service.event;

import by.Egrius.notification_service.entity.TransferStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class TransferProcessedEvent {
    private UUID userPublicTd;
    private BigDecimal amount;
    private UUID fromAccountPublicId;
    private UUID toAccountPublicId;
    private UUID transferPublicId;
    private TransferStatus status;

    public TransferProcessedEvent(Object source, UUID userPublicTd,
                                  UUID fromAccountPublicId, UUID toAccountPublicId,
                                  UUID transferPublicId, BigDecimal amount,
                                  TransferStatus status
    ) {

        this.userPublicTd = userPublicTd;
        this.fromAccountPublicId = fromAccountPublicId;
        this.toAccountPublicId = toAccountPublicId;
        this.transferPublicId = transferPublicId;
        this.amount = amount;
        this.status = status;
    }

}
