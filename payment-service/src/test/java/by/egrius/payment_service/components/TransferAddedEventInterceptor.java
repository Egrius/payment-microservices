package by.egrius.payment_service.components;

import by.egrius.payment_service.event.TransferAddedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TransferAddedEventInterceptor {
    private final List<TransferAddedEvent> capturedEvents = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    @RabbitListener(queues = "interceptor.queue")
    public void interceptTransferAddedEvent(TransferAddedEvent event) {
        log.info("Intercepted TransferAddedEvent: transferId={}", event.getTransferId());
        capturedEvents.add(event);
        latch.countDown();
    }

    public TransferAddedEvent getCapturedEvent() throws InterruptedException {
        latch.await(10, TimeUnit.SECONDS);
        return capturedEvents.isEmpty() ? null : capturedEvents.get(0);
    }

    public void clear() {
        capturedEvents.clear();
    }
}
