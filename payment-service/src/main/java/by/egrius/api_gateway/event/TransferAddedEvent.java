package by.egrius.api_gateway.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TransferAddedEvent extends ApplicationEvent {
    private final long fromAccountId;
    private final long toAccountId;
    private final long transferId;

    public TransferAddedEvent(Object source, long fromAccountId, long toAccountId, long transferId) {
        super(source);
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.transferId = transferId;
    }
}
