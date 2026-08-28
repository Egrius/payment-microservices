package by.egrius.payment_service.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class TransferAddedEvent implements Serializable {
    private long fromAccountId;
    private long toAccountId;
    private long transferId;
    private UUID transferPublicId;
    private UUID userId;

    public TransferAddedEvent(UUID userId, UUID transferPublicId, long fromAccountId, long toAccountId, long transferId) {
        this.userId = userId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.transferId = transferId;
        this.transferPublicId = transferPublicId;
    }
}
