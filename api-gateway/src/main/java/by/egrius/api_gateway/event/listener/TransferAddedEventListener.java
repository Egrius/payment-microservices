package by.egrius.api_gateway.event.listener;

import by.egrius.api_gateway.event.TransferAddedEvent;
import by.egrius.api_gateway.service.TransferProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@Slf4j
@RequiredArgsConstructor
public class TransferAddedEventListener {

    private final TransferProcessor transferProcessor;

    @Async("transfer-task-pool")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationEvent(TransferAddedEvent event) {

        log.debug("Got event: {}", event);

        log.debug("Calling 'TransferProcessor.processTransfer()'" +
                " with params fromAccountId: {} , toAccountId: {} , transferId: {}",
                event.getFromAccountId(), event.getToAccountId(), event.getTransferId());

        transferProcessor.processTransfer(event.getFromAccountId(), event.getToAccountId(), event.getTransferId());
    }
}
