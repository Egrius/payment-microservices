package by.egrius.payment_service.event.publisher;

import by.egrius.payment_service.event.TransferAddedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferAddedEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishEvent(Object event) {
        if(event instanceof TransferAddedEvent) {

            log.info("Publishing 'TransferAddedEvent' event for a transfer with id {}",
                    ((TransferAddedEvent)event).getTransferId());

            applicationEventPublisher.publishEvent(event);
        }
    }
}
