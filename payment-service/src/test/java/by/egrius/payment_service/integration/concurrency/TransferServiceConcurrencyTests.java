package by.egrius.payment_service.integration.concurrency;

import by.egrius.payment_service.components.TransferAddedEventInterceptor;
import by.egrius.payment_service.context.ServiceIntegrationTestContext;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.event.TransferAddedEvent;
import by.egrius.payment_service.event.TransferProcessedEvent;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.components.TestNotificationListener;
import by.egrius.payment_service.integration.config.TestRabbitMQConfig;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.service.AccountService;
import by.egrius.payment_service.service.TransferProcessor;
import by.egrius.payment_service.service.TransferService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@Profile("test")
@Slf4j
@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(TestRabbitMQConfig.class)
public class TransferServiceConcurrencyTests extends BaseIntegrationTest {
    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TestNotificationListener notificationListener;

    @MockitoSpyBean
    private TransferProcessor transferProcessor;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferAddedEventInterceptor transferAddedEventInterceptor;

    private UUID fromUserPublicId;
    private UUID toUserPublicId;

    private UUID fromAccountPublicId;
    private UUID toAccountPublicId;

    private Account fromAccount;
    private Account toAccount;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void setUp() {
        rabbitAdmin.purgeQueue("processing.queue");
        rabbitAdmin.purgeQueue("notification.queue");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        notificationListener.clear();

        fromUserPublicId = UUID.randomUUID();
        toUserPublicId = UUID.randomUUID();

        fromAccount = accountRepository.save(Account.builder()
                        .userId(fromUserPublicId)
                        .name("From_Account")
                        .balance(BigDecimal.valueOf(1000L))
                        .currency("USD")
                        .build());

        toAccount = accountRepository.save(Account.builder()
                .userId(toUserPublicId)
                .name("To_Account")
                .balance(BigDecimal.valueOf(1000L))
                .currency("USD")
                .build());

        fromAccountPublicId = fromAccount.getPublicId();
        toAccountPublicId = toAccount.getPublicId();
    }

    @Test
    void shouldHandleManyTransfersAndHandleExceptions() throws InterruptedException {
        int threadCount = 10;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch awaitLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        notificationListener.setLatch(awaitLatch);

        BigDecimal amountToTransfer = BigDecimal.valueOf(150L);
        TransferCreateDto transferCreateDto = new TransferCreateDto(fromAccountPublicId, toAccountPublicId, amountToTransfer);

        List<CompletableFuture<TransferReadDto>> futures = new ArrayList<>();
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        BigDecimal balance = fromAccount.getBalance();

        int maxPossibleSuccess = balance.divideToIntegralValue(amountToTransfer).intValue();
        long expectedSuccess = Math.min(threadCount, maxPossibleSuccess);
        long expectedErrors = threadCount - expectedSuccess;

        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    return transferService.createTransfer(transferCreateDto, fromUserPublicId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    exceptions.add(e);
                    return null;
                }
            }, executorService));
        }

        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<TransferReadDto> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        assertEquals(threadCount, results.size(),
                "All transfers should be created, even if they will fail later");

        boolean completed = notificationListener.getLatch().await(30, TimeUnit.SECONDS);

        assertTrue("Not all transfers were processed in time", completed);

        List<TransferProcessedEvent> processedEvents = notificationListener.getEvents();
        assertEquals(threadCount, processedEvents.size(),
                "Should receive event for each transfer");

        Map<TransferStatus, Long> resultsByStatus = processedEvents.stream()
                .collect(Collectors.groupingBy(
                        TransferProcessedEvent::getStatus,
                        Collectors.counting()
                ));

        Long actualSuccess = resultsByStatus.getOrDefault(TransferStatus.COMPLETED, 0L);
        Long actualFailed = resultsByStatus.getOrDefault(TransferStatus.FAILED, 0L);

        assertEquals(expectedSuccess, actualSuccess,
                String.format("Expected %d successful transfers, but got %d",
                        expectedSuccess, actualSuccess));
        assertEquals(expectedErrors, actualFailed,
                String.format("Expected %d failed transfers, but got %d",
                        expectedErrors, actualFailed));

        Account finalFromAccount = accountRepository.findByPublicId(fromAccountPublicId)
                .orElseThrow();
        BigDecimal expectedFinalBalance = balance.subtract(
                amountToTransfer.multiply(BigDecimal.valueOf(actualSuccess))
        );
        assertEquals(0, expectedFinalBalance.compareTo(finalFromAccount.getBalance()),
                "Final balance doesn't match expected");

        assertTrue("Unexpected exceptions during transfer creation: " + exceptions,
                exceptions.isEmpty());
    }

    @Test
    void shouldNotDeadlockWhenTransferringBetweenSameAccounts() throws InterruptedException {

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch awaitLatch = new CountDownLatch(2);

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        notificationListener.setLatch(awaitLatch);

        BigDecimal amountToTransfer = BigDecimal.valueOf(150L);
        TransferCreateDto firstTransferCreateDto = new TransferCreateDto(fromAccountPublicId, toAccountPublicId, amountToTransfer);
        TransferCreateDto secondTransferCreateDto = new TransferCreateDto(toAccountPublicId, fromAccountPublicId, amountToTransfer);


        List<CompletableFuture<TransferReadDto>> futures = new ArrayList<>();
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        BigDecimal fromBalanceBefore = fromAccount.getBalance();
        BigDecimal toBalanceBefore = toAccount.getBalance();

        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
                return transferService.createTransfer(firstTransferCreateDto, fromUserPublicId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (Exception e) {
                exceptions.add(e);
                return null;
            }
        }, executorService));

        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
                return transferService.createTransfer(secondTransferCreateDto, toUserPublicId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (Exception e) {
                exceptions.add(e);
                return null;
            }
        }, executorService));

        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<TransferReadDto> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        awaitLatch.await(60, TimeUnit.SECONDS);

        List<TransferProcessedEvent> processedEvents = notificationListener.getEvents();
        assertEquals(2, processedEvents.size(),
                "Should receive event for each transfer");

        Map<TransferStatus, Long> resultsByStatus = processedEvents.stream()
                .collect(Collectors.groupingBy(
                        TransferProcessedEvent::getStatus,
                        Collectors.counting()
                ));

        Long actualSuccess = resultsByStatus.getOrDefault(TransferStatus.COMPLETED, 0L);
        Long actualFailed = resultsByStatus.getOrDefault(TransferStatus.FAILED, 0L);

        try{

            assertEquals(2, actualSuccess,
                    String.format("Expected %d successful transfers, but got %d",
                            2, actualSuccess));
            assertEquals(0, actualFailed,
                    String.format("Expected %d failed transfers, but got %d",
                            0, actualFailed));
        } catch (AssertionFailedError er) {
            log.warn("Transfers' statuses are not equal to the expected ones, count of 'PENDING' = {} ",
                    resultsByStatus.getOrDefault(TransferStatus.PENDING, 0L));
            throw er;
        }

        List<Transfer> transfersFromDB = transferRepository.findAll();

        System.out.println(transfersFromDB);

        int succeeded = transfersFromDB.stream().filter(t -> t.getStatus() == TransferStatus.COMPLETED)
                .toList().size();

        assertEquals(2, succeeded);
    }

    @Test
    void shouldHandleDuplicateProcessingOfSameTransfer() throws InterruptedException {

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch awaitLatch = new CountDownLatch(1);
        CountDownLatch duplicateLatch = new CountDownLatch(1);

        ExecutorService executorService = Executors.newFixedThreadPool(1);

        notificationListener.clear();
        notificationListener.setLatch(awaitLatch);

        BigDecimal amountToTransfer = BigDecimal.valueOf(150L);
        TransferCreateDto transferCreateDto = new TransferCreateDto(
                fromAccountPublicId,
                toAccountPublicId,
                amountToTransfer
        );

        List<CompletableFuture<TransferReadDto>> futures = new ArrayList<>();
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        BigDecimal fromBalanceBefore = fromAccount.getBalance();
        BigDecimal toBalanceBefore = toAccount.getBalance();

        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
                return transferService.createTransfer(transferCreateDto, fromUserPublicId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (Exception e) {
                exceptions.add(e);
                return null;
            }
        }, executorService));

        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<TransferReadDto> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();


        boolean firstProcessed = awaitLatch.await(30, TimeUnit.SECONDS);
        assertTrue("First transfer should be processed", firstProcessed);


        List<TransferProcessedEvent> processedEvents = notificationListener.getEvents();
        assertEquals(1, processedEvents.size(), "Should receive event for first transfer");

        TransferProcessedEvent firstEvent = processedEvents.getFirst();
        assertEquals(TransferStatus.COMPLETED, firstEvent.getStatus(),
                "First transfer should be COMPLETED");


        List<Transfer> transfersFromDB = transferRepository.findAll();
        assertEquals(1, transfersFromDB.size(), "Should have one transfer in DB");

        int succeeded = transfersFromDB.stream()
                .filter(t -> t.getStatus() == TransferStatus.COMPLETED)
                .toList().size();
        assertEquals(1, succeeded, "First transfer should be COMPLETED in DB");

        TransferAddedEvent addedEvent = transferAddedEventInterceptor.getCapturedEvent();
        assertNotNull(addedEvent, "Should capture TransferAddedEvent");

        notificationListener.clear();
        notificationListener.setLatch(duplicateLatch);


        log.info("Sending duplicate event for transfer: {}", addedEvent.getTransferId());
        assertDoesNotThrow(() -> transferProcessor.processTransfer(addedEvent));

        verify(transferProcessor, times(1)).handleTransferFailure(
                eq(addedEvent.getTransferId()),
                eq(addedEvent.getFromAccountId()),
                eq(addedEvent.getToAccountId()),
                any(String.class));


        boolean duplicateProcessed = duplicateLatch.await(30, TimeUnit.SECONDS);
        assertFalse(duplicateProcessed, "Duplicate should not be processed");


        List<TransferProcessedEvent> duplicateEvents = notificationListener.getEvents();
        assertTrue("Should not create processed event for the same transfer twice",
                duplicateEvents.isEmpty());

        List<Transfer> finalTransfers = transferRepository.findAll();
        assertEquals(1, finalTransfers.size(),
                "Should still have only one transfer in DB");

        long finalSucceeded = finalTransfers.stream()
                .filter(t -> t.getStatus() == TransferStatus.COMPLETED)
                .count();
        assertEquals(1, finalSucceeded,
                "Still only one COMPLETED transfer in DB");


        Account finalFromAccount = accountRepository.findByPublicId(fromAccountPublicId)
                .orElseThrow();
        Account finalToAccount = accountRepository.findByPublicId(toAccountPublicId)
                .orElseThrow();


        BigDecimal expectedFromBalance = BigDecimal.valueOf(850L);
        BigDecimal expectedToBalance = BigDecimal.valueOf(1150L);

        assertEquals(0, expectedFromBalance.compareTo(finalFromAccount.getBalance()),
                "From account balance should be 850 (unchanged after duplicate)");
        assertEquals(0, expectedToBalance.compareTo(finalToAccount.getBalance()),
                "To account balance should be 1150 (unchanged after duplicate)");


        BigDecimal totalBefore = fromBalanceBefore.add(toBalanceBefore);
        BigDecimal totalAfter = finalFromAccount.getBalance().add(finalToAccount.getBalance());
        assertEquals(0, totalBefore.compareTo(totalAfter),
                "Total balance should remain constant");


        assertTrue("Unexpected exceptions during transfer creation: " + exceptions,
                exceptions.isEmpty());

        log.info("Duplicate processing test passed!");
    }
}