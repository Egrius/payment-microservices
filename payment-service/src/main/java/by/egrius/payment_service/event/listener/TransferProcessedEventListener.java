package by.egrius.payment_service.event.listener;

import by.egrius.payment_service.event.TransferProcessedEvent;
import by.egrius.payment_service.service.TransferProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransferProcessedEventListener {

    private final TransferProcessor transferProcessor;

    // it should make an api call to send a notification to a user
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationEvent(TransferProcessedEvent event) {

        log.debug("Got event: {}", event);

        log.debug("Calling 'TransferProcessor.processTransfer()'" +
                        " with params fromAccountPublicId: {} , toAccountPublicId: {} , publicId: {}",
                event.getFromAccountId(), event.getToAccountId(), event.getTransferId());

    }
}