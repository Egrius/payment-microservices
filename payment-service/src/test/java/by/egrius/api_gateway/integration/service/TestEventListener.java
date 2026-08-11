package by.egrius.api_gateway.integration.service;

import by.egrius.api_gateway.event.TransferProcessedEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@Getter
public class TestEventListener {

    private final List<TransferProcessedEvent> events = new ArrayList<>();
    private CountDownLatch latch;

    private final AtomicInteger counter = new AtomicInteger(0);

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    @EventListener
    public void onTransferProcessed(TransferProcessedEvent event) {
        events.add(event);
        log.info("🔥 TEST EVENT RECEIVED for transfer: {}", event.getTransferId());
        if (latch != null) {
            latch.countDown();
        }
        counter.incrementAndGet();
        log.info("------- EVENT GOT: {}", counter.get());
    }

    public void reset() {
        events.clear();
        latch = null;
    }
}