package by.egrius.payment_service.integration;

import by.egrius.payment_service.components.StatefulRetryTestListener;
import by.egrius.payment_service.context.ServiceIntegrationTestContext;
import by.egrius.payment_service.event.TransferAddedEvent;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.integration.config.TestCacheConfig;
import by.egrius.payment_service.integration.config.TestRabbitMQConfig;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.service.TransferProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
@ActiveProfiles("test")
@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import({
        TestCacheConfig.class,
        TestRabbitMQConfig.class,
        StatefulRetryTestListener.class
})
public class StatefulRetryTest extends BaseIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private StatefulRetryTestListener statefulRetryTestListener;

    @MockitoBean
    private TransferProcessor transferProcessor;

    private static final AtomicInteger invocationCount = new AtomicInteger(0);

    @BeforeEach
    void reset() {
        statefulRetryTestListener.getInvocationCount().set(0);
        statefulRetryTestListener.getTransferRepository().deleteAll();
    }

    @Test
    void shouldRetryAndRollbackTransaction() {

        rabbitTemplate.convertAndSend(
                "processing.exchange",
                "transfer.processing",
                new TransferAddedEvent(
                        UUID.randomUUID(), UUID.randomUUID(), 1L, 2L, 3L
                ),
                message -> {
                    message.getMessageProperties().setMessageId(UUID.randomUUID().toString());
                    return message;
                }
        );

        Awaitility.await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(statefulRetryTestListener.getInvocationCount().get()).isEqualTo(3);
        });

        assertThat(transferRepository.count()).isEqualTo(0);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TransferAddedEvent dead = (TransferAddedEvent) rabbitTemplate.receiveAndConvert("dead.letter.queue");

            assertThat(dead).isNotNull();
            assertThat(dead.getTransferId()).isEqualTo(3L);
        });
    }
}