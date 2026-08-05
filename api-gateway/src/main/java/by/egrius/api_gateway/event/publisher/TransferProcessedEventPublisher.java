package by.egrius.api_gateway.event.publisher;

import by.egrius.api_gateway.event.TransferAddedEvent;
import by.egrius.api_gateway.event.TransferProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferProcessedEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishEvent(Object event) {
        if(event instanceof TransferProcessedEvent) {

            log.info("Publishing 'TransferProcessedEvent' event for a transfer with id {}",
                    ((TransferProcessedEvent)event).getTransferId());

            applicationEventPublisher.publishEvent(event);
        }
    }
}
