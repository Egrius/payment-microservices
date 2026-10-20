package by.Egrius.notification_service.event;

import by.Egrius.notification_service.entity.TransferStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class TransferProcessedEvent {
    private UUID userPublicId;
    private BigDecimal amount;
    private UUID fromAccountPublicId;
    private UUID toAccountPublicId;
    private UUID transferPublicId;
    private TransferStatus status;

    public TransferProcessedEvent(Object source, UUID userPublicId,
                                  UUID fromAccountPublicId, UUID toAccountPublicId,
                                  UUID transferPublicId, BigDecimal amount,
                                  TransferStatus status
    ) {

        this.userPublicId = userPublicId;
        this.fromAccountPublicId = fromAccountPublicId;
        this.toAccountPublicId = toAccountPublicId;
        this.transferPublicId = transferPublicId;
        this.amount = amount;
        this.status = status;
    }

}
