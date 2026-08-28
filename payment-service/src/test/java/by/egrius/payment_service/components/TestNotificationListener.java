package by.egrius.payment_service.components;

import by.egrius.payment_service.event.TransferProcessedEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Component
@Slf4j
public class TestNotificationListener {

    private final List<TransferProcessedEvent> events = new CopyOnWriteArrayList<>();
    private CountDownLatch latch;
    private final AtomicInteger receivedCount = new AtomicInteger(0);

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    @RabbitListener(queues = "notification.queue")
    public void onTransferProcessed(TransferProcessedEvent event) {
        log.info("📨 Received TransferProcessedEvent: transferId={}, status={}",
                event.getTransferPublicId(), event.getStatus());
        events.add(event);
        receivedCount.incrementAndGet();
        log.debug("Received event for transfer: {}", event.getTransferPublicId());
        if (latch != null) {
            latch.countDown();
        }
    }

    public int getReceivedCount() {
        return receivedCount.get();
    }

    public void clear() {
        events.clear();
        receivedCount.set(0);
        latch = null;
    }
}
