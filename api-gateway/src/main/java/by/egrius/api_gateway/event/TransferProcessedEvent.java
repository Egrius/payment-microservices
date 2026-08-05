package by.egrius.api_gateway.event;

import by.egrius.api_gateway.entity.TransferStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class TransferProcessedEvent extends ApplicationEvent {
    private final BigDecimal amount;
    private final long fromAccountId;
    private final long toAccountId;
    private final long transferId;
    private final TransferStatus status;

    public TransferProcessedEvent(Object source, long fromAccountId, long toAccountId, long transferId, BigDecimal amount, TransferStatus status) {
        super(source);
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.transferId = transferId;
        this.amount = amount;
        this.status = status;
    }
}
