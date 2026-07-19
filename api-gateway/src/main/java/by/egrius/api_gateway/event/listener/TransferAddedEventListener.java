package by.egrius.api_gateway.event.listener;

import by.egrius.api_gateway.event.TransferAddedEvent;
import by.egrius.api_gateway.service.TransferTask;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class TransferAddedEventListener {

    private final ObjectProvider<TransferTask> transferTaskObjectProvider;

    @EventListener
    @Async("transfer-task-pool")
    public void onApplicationEvent(TransferAddedEvent event) {
        TransferTask task = transferTaskObjectProvider.getObject(event.getFromAccountId(), event.getToAccountId(), event.getTransferId());
        task.processTransfer();
    }
}
