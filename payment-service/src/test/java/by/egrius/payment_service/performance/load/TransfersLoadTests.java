package by.egrius.payment_service.performance.load;

import by.egrius.payment_service.components.TestNotificationListener;
import by.egrius.payment_service.context.ServiceIntegrationTestContext;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.integration.config.TestRabbitMQConfig;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.service.TransferService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ActiveProfiles("test")
@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(TestRabbitMQConfig.class)
@Slf4j
public class TransfersLoadTests extends BaseIntegrationTest {

    @Autowired
    private TestNotificationListener notificationListener;

    @Autowired
    private TransferService transferService;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    private UUID fromUserId;
    private UUID toUserId;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {

        notificationListener.clear();

        rabbitAdmin.purgeQueue("processing.queue");
        rabbitAdmin.purgeQueue("notification.queue");

        transferRepository.deleteAll();
        accountRepository.deleteAll();

        fromUserId = UUID.randomUUID();
        toUserId = UUID.randomUUID();

        fromAccount = accountRepository.save(Account.builder()
                .publicId(UUID.randomUUID())
                .userId(fromUserId)
                .currency("USD")
                .name("FromAccount")
                .balance(BigDecimal.valueOf(1_000_000))
                .build());

        toAccount = accountRepository.save(Account.builder()
                .publicId(UUID.randomUUID())
                .userId(toUserId)
                .currency("USD")
                .name("ToAccount")
                .balance(BigDecimal.valueOf(1_000_000))
                .build());
    }

    @Test
    void shouldHandle1000Calls() throws Exception {
        int totalCalls = 1000;
        int threadPoolSize = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        AtomicInteger sendErrorCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(100)
        );

        log.info("Starting load test: {} calls with {} threads", totalCalls, threadPoolSize);

        CountDownLatch eventsLatch = new CountDownLatch(totalCalls);
        notificationListener.setLatch(eventsLatch);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalCalls; i++) {
            final int callNumber = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    transferService.createTransfer(createDto, fromUserId);
                } catch (Exception e) {
                    sendErrorCount.incrementAndGet();
                    log.error("Error in call {}: {}", callNumber, e.getMessage());
                }
            }, executor);
            futures.add(future);
        }


        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

        long sendTime = System.currentTimeMillis() - startTime;
        log.info("All {} messages sent in {} ms", totalCalls, sendTime);


        boolean allEventsReceived = eventsLatch.await(60, TimeUnit.SECONDS);

        long totalDuration = System.currentTimeMillis() - startTime;

        long dbCount = transferRepository.count();
        List<Transfer> allTransfers = transferRepository.findAll();
        long completedCount = allTransfers.stream()
                .filter(t -> t.getStatus() == TransferStatus.COMPLETED || t.getStatus() == TransferStatus.FAILED)
                .count();

        log.info("=== LOAD TEST RESULTS ===");
        log.info("Total calls: {}", totalCalls);
        log.info("Send errors: {}", sendErrorCount.get());
        log.info("Events received: {}", notificationListener.getReceivedCount());
        log.info("All events received: {}", allEventsReceived);
        log.info("DB records: {}", dbCount);
        log.info("Completed/Failed in DB: {}", completedCount);
        log.info("Send time: {} ms", sendTime);
        log.info("Total duration: {} ms", totalDuration);
        log.info("Throughput: {} calls/second", (totalCalls * 1000.0) / totalDuration);

        assertThat(sendErrorCount.get()).isZero();
        assertThat(allEventsReceived).isTrue();
        assertThat(notificationListener.getReceivedCount()).isEqualTo(totalCalls);
        assertThat(dbCount).isEqualTo(totalCalls);
        assertThat(completedCount).isEqualTo(totalCalls);

        for (Transfer t : allTransfers) {
            assertThat(t.getProcessedAt()).isNotNull();
            assertThat(t.getStatus()).isIn(TransferStatus.COMPLETED, TransferStatus.FAILED);
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    /*
    Test to see optimal threads count for a better RPS
     */
    @Test
    void shouldComparePools() throws InterruptedException {

        Map<Integer, Double> results = new LinkedHashMap<>();
        int[] poolSizes= {10, 20, 30, 40, 50, 60, 70, 80};
        int totalCalls = 1000;

        for (int size : poolSizes) {

            notificationListener.clear();
            transferRepository.deleteAll();
            accountRepository.deleteAll();
            rabbitAdmin.purgeQueue("processing.queue");
            rabbitAdmin.purgeQueue("notification.queue");

            fromAccount = accountRepository.save(Account.builder()
                    .publicId(UUID.randomUUID())
                    .userId(fromUserId)
                    .currency("USD")
                    .name("FromAccount")
                    .balance(BigDecimal.valueOf(1_000_000))
                    .build());
            toAccount = accountRepository.save(Account.builder()
                    .publicId(UUID.randomUUID())
                    .userId(toUserId)
                    .currency("USD")
                    .name("ToAccount")
                    .balance(BigDecimal.valueOf(1_000_000))
                    .build());

            accountRepository.flush();

            assertThat(accountRepository.findById(fromAccount.getId())).isPresent();
            assertThat(accountRepository.findById(toAccount.getId())).isPresent();

            TransferCreateDto createDto = new TransferCreateDto(
                    fromAccount.getPublicId(),
                    toAccount.getPublicId(),
                    BigDecimal.valueOf(100)
            );

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            ExecutorService executor = Executors.newFixedThreadPool(size);
            CountDownLatch eventsLatch = new CountDownLatch(totalCalls);
            AtomicInteger sendErrorCount = new AtomicInteger(0);

            notificationListener.setLatch(eventsLatch);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < totalCalls; i++) {
                final int callNumber = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        transferService.createTransfer(createDto, fromUserId);
                    } catch (Exception e) {
                        sendErrorCount.incrementAndGet();
                        log.error("Error in call {}: {}", callNumber, e.getMessage());
                    }
                }, executor);
                futures.add(future);
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }

            long sendTime = System.currentTimeMillis() - startTime;
            log.info("All {} messages sent in {} ms", totalCalls, sendTime);

            boolean allEventsReceived;
            try {
                allEventsReceived = eventsLatch.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            long totalDuration = System.currentTimeMillis() - startTime;

            List<Transfer> allTransfers = transferRepository.findAll();
            Map<TransferStatus, Long>  transferStats = allTransfers.stream()
                    .collect(Collectors.groupingBy(
                            Transfer::getStatus,
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));


            long completedCount = transferStats.getOrDefault(TransferStatus.COMPLETED, 0L);
            long failedCount = transferStats.getOrDefault(TransferStatus.FAILED, 0L);
            long pendingCount = transferStats.getOrDefault(TransferStatus.PENDING, 0L);

            double tps = (totalCalls * 1000.0) / totalDuration;
            results.put(size, tps);

            log.info("=== RESULTS FOR POOL SIZE = {} ===", size);
            log.info("Total calls: {}", totalCalls);
            log.info("Send errors: {}", sendErrorCount.get());
            log.info("Events received: {} / {}", notificationListener.getReceivedCount(), totalCalls);
            log.info("All events received: {}", allEventsReceived);
            log.info("DB records: {}", allTransfers.size());
            log.info("'COMPLETED' count: {}", completedCount);
            log.info("'FAILED' count: {}", failedCount);
            log.info("'PENDING' count: {}", pendingCount);
            log.info("Send time: {} ms", sendTime);
            log.info("Total duration: {} ms", totalDuration);
            log.info("Throughput: {:.2f} calls/second", tps);

            if (!allEventsReceived) {
                log.warn("⚠️ Not all events received! Missing: {}",
                        totalCalls - notificationListener.getReceivedCount());
            }

            if (pendingCount > 0) {
                log.warn("⚠️ {} transfers still PENDING!", pendingCount);
            }

            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            Thread.sleep(2000);

        }

        log.info("\n=== OPTIMAL POOL SIZE RESULTS ===");
        results.forEach((size, tps) ->
                log.info("Pool {}: {} TPS", size, tps)
        );

        Map.Entry<Integer, Double> optimal = results.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (optimal != null) {
            log.info("\n🎯 OPTIMAL POOL SIZE: {} with {} TPS",
                    optimal.getKey(), optimal.getValue());
        }
    }

}