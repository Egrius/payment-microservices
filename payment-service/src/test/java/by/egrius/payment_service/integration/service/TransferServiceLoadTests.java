package by.egrius.payment_service.integration.service;

import by.egrius.payment_service.context.ServiceIntegrationTestContext;
import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.optimization.config.BaseOptimizingTest;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.service.AccountService;
import by.egrius.payment_service.service.TransferService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(TestEventListener.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TransferServiceLoadTests extends BaseOptimizingTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TestEventListener testEventListener;

    private static final int CONCURRENT_TRANSFERS = 1000;
    private UUID userPublicId;
    private AccountReadDto fromAccount;
    private AccountReadDto toAccount;

    @BeforeEach
    void resetListener() {
        testEventListener.reset();
    }
// Requires a message broker to make  high-load tests
//    @BeforeAll
//    @Transactional
//    void setUp() {
//        log.info("🏦 Creating test accounts...");
//        userPublicId = UUID.randomUUID();
//
//        Account from = Account.builder()
//                .publicId(UUID.randomUUID())
//                .userId(userPublicId)
//                .balance(BigDecimal.valueOf(1_000_000))
//                .currency("USD")
//                .name("LoadTest_From")
//                .build();
//
//        Account to = Account.builder()
//                .publicId(UUID.randomUUID())
//                .userId(userPublicId)
//                .balance(BigDecimal.ZERO)
//                .currency("USD")
//                .name("LoadTest_To")
//                .build();
//
//        accountRepository.save(from);
//        accountRepository.save(to);
//
//        fromAccount = new AccountReadDto(
//                from.getPublicId(),
//                from.getName(),
//                from.getCurrency(),
//                from.getBalance()
//        );
//        toAccount = new AccountReadDto(
//                to.getPublicId(),
//                to.getName(),
//                to.getCurrency(),
//                to.getBalance()
//        );
//
//        log.info("✅ Accounts created: from={}, to={}", fromAccount.publicId(), toAccount.publicId());
//    }

//    @Test
//    void test_1000_transfersToSingleAccount() throws Exception {
//
//        CountDownLatch latch = new CountDownLatch(CONCURRENT_TRANSFERS);
//        testEventListener.setLatch(latch);
//
//
//        ExecutorService executor = Executors.newFixedThreadPool(50);
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//
//        long createStart = System.currentTimeMillis();
//
//        for (int i = 0; i < CONCURRENT_TRANSFERS; i++) {
//            final int index = i;
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                TransferCreateDto dto = new TransferCreateDto(
//                        fromAccount.publicId(),
//                        toAccount.publicId(),
//                        BigDecimal.valueOf(10 + (index % 10) * 10)
//                );
//                try {
//                    transferService.createTransfer(dto, userPublicId);
//                } catch (Exception e) {
//                    log.warn("❌ Failed to create transfer #{}: {}", index, e.getMessage());
//                }
//            }, executor);
//            futures.add(future);
//        }
//
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
//                .join();
//
//        long createDuration = System.currentTimeMillis() - createStart;
//        log.info("✅ All {} transfers created in {} ms", CONCURRENT_TRANSFERS, createDuration);
//
//        log.info("⏳ Waiting for all {} transfers to be processed...", CONCURRENT_TRANSFERS);
//        long processStart = System.currentTimeMillis();
//
//        boolean completed = latch.await(30, TimeUnit.SECONDS);
//        long processDuration = System.currentTimeMillis() - processStart;
//
//        if (!completed) {
//            log.error("❌ Only {} of {} transfers processed in time",
//                    testEventListener.getEvents().size(), CONCURRENT_TRANSFERS);
//        }
//
//        assertThat(testEventListener.getEvents()).hasSize(CONCURRENT_TRANSFERS);
//
//        long successCount = testEventListener.getEvents().stream()
//                .filter(e -> e.getStatus() == TransferStatus.COMPLETED)
//                .count();
//
//        log.info("""
//            ====================================================
//            📊 CONCURRENT TRANSFER TEST RESULTS
//            ====================================================
//            Total transfers:       {}
//            Successful:            {} ✅
//            Failed:                {} ❌
//            Create time:           {} ms
//            Processing time:       {} ms
//            Total time:            {} ms
//            ====================================================
//            """,
//                CONCURRENT_TRANSFERS,
//                successCount,
//                CONCURRENT_TRANSFERS - successCount,
//                createDuration,
//                processDuration,
//                createDuration + processDuration
//        );
//
//        assertThat(successCount).isEqualTo(CONCURRENT_TRANSFERS);
//    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DeadLockTests {

        private final UUID accountPublicId_A = UUID.randomUUID();
        private final UUID accountPublicId_B = UUID.randomUUID();

        private final UUID publicUserId_A = UUID.randomUUID();
        private final UUID publicUserId_B = UUID.randomUUID();

        private final BigDecimal accountBalance_A = BigDecimal.valueOf(1_000);
        private final BigDecimal accountBalance_B = BigDecimal.valueOf(1_000);

        @BeforeAll
        void init() {

            Account accountA = Account.builder()
                .publicId(accountPublicId_A)
                .userId(publicUserId_A)
                .balance(accountBalance_A)
                .currency("USD")
                .name("Account_A")
                .build();

            Account accountB = Account.builder()
                    .publicId(accountPublicId_B)
                    .userId(publicUserId_B)
                    .balance(accountBalance_B)
                    .currency("USD")
                    .name("Account_B")
                    .build();

            accountRepository.saveAll(List.of(accountA, accountB));
        }

        @Test
        void shouldCompleteTwoParallelTransfersBetweenTwoAccounts() throws InterruptedException {
            testEventListener.reset();
            CountDownLatch processedTransfersLatch = new CountDownLatch(2);
            testEventListener.setLatch(processedTransfersLatch);

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            BigDecimal from_A_to_B_amount = BigDecimal.valueOf(200L);
            // From A to B (200 USD)
            CompletableFuture<TransferReadDto> firstTransferCreation = CompletableFuture.supplyAsync(() -> {
                TransferCreateDto createDto = new TransferCreateDto(accountPublicId_A, accountPublicId_B, from_A_to_B_amount);
                return transferService.createTransfer(createDto, publicUserId_A);
            });


            BigDecimal from_B_to_A_amount = BigDecimal.valueOf(400L);
            // From B to A (200 USD)
            CompletableFuture<TransferReadDto> secondTransferCreation = CompletableFuture.supplyAsync(() -> {
                TransferCreateDto createDto = new TransferCreateDto(accountPublicId_B, accountPublicId_A, from_B_to_A_amount);
                return transferService.createTransfer(createDto, publicUserId_B);
            });

            boolean completed = processedTransfersLatch.await(30, TimeUnit.SECONDS);

            if(!completed) throw new RuntimeException("Failed to process transfers");

            Account accountAfterTransfer_A = accountRepository.findByPublicId(accountPublicId_A)
                    .orElseThrow(() -> new RuntimeException("Can't find Account_A"));

            Account accountAfterTransfer_B = accountRepository.findByPublicId(accountPublicId_B)
                    .orElseThrow(() -> new RuntimeException("Can't find Account_B"));

            assertEquals(
                    accountBalance_A.subtract(from_A_to_B_amount).add(from_B_to_A_amount).doubleValue(),
                    accountAfterTransfer_A.getBalance().doubleValue());

            assertEquals(
                    accountBalance_B.subtract(from_B_to_A_amount).add(from_A_to_B_amount).doubleValue(),
                    accountAfterTransfer_B.getBalance().doubleValue());
        }

        @Test
        void shouldHandleCircularTransfersWithoutDeadlocks() throws Exception {

            int accountCount = 5;
            List<UUID> accountIds = new ArrayList<>();
            List<UUID> userIds = new ArrayList<>();

            for (int i = 0; i < accountCount; i++) {
                UUID userId = UUID.randomUUID();
                userIds.add(userId);
                UUID accountId = UUID.randomUUID();
                accountIds.add(accountId);

                Account account = Account.builder()
                        .publicId(accountId)
                        .userId(userId)
                        .balance(BigDecimal.valueOf(1000))
                        .currency("USD")
                        .name("CircularTest_Account_" + i)
                        .build();
                accountRepository.save(account);
            }

            int totalTransfers = 50;
            CountDownLatch latch = new CountDownLatch(totalTransfers);
            testEventListener.setLatch(latch);

            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < totalTransfers; i++) {
                int fromIdx = i % accountCount;
                int toIdx = (i + 1) % accountCount;

                int pos = i;

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    TransferCreateDto dto = new TransferCreateDto(
                            accountIds.get(fromIdx),
                            accountIds.get(toIdx),
                            BigDecimal.valueOf(10 + (pos % 5) * 10)
                    );
                    try {
                        transferService.createTransfer(dto, userIds.get(fromIdx));
                    } catch (Exception e) {
                        log.warn("Transfer failed: {}", e.getMessage());
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .join();

            boolean completed = latch.await(60, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            for (int i = 0; i < accountCount; i++) {
                Account account = accountRepository.findByPublicId(accountIds.get(i))
                        .orElseThrow();
                assertThat(account.getBalance())
                        .withFailMessage("Account %s has invalid balance", accountIds.get(i))
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }

            BigDecimal totalBalance = accountIds.stream()
                            .map(accountRepository::findByPublicId)
                            .map(opt -> opt.orElseThrow())
                            .map(Account::getBalance)
                            .toList()
                    .stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalBalance).isEqualByComparingTo(BigDecimal.valueOf(accountCount * 1000));
        }
    }

    @Test
    void shouldNeverAllowNegativeBalanceUnderHeavyLoad() throws Exception {
        int totalTransfers = 50;
        BigDecimal initialBalance = BigDecimal.valueOf(1000);
        BigDecimal transferAmount = BigDecimal.valueOf(150); // 1000 / 150 ≈ 6 успешных

        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account account = Account.builder()
                .publicId(accountId)
                .userId(userId)
                .balance(initialBalance)
                .currency("USD")
                .name("NegativeTest_From")
                .build();
        accountRepository.save(account);

        Account toAccount = Account.builder()
                .publicId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .name("NegativeTest_To")
                .build();
        accountRepository.save(toAccount);

        CountDownLatch latch = new CountDownLatch(totalTransfers);
        testEventListener.setLatch(latch);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalTransfers; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                TransferCreateDto dto = new TransferCreateDto(
                        accountId,
                        toAccount.getPublicId(),
                        transferAmount
                );
                try {
                    transferService.createTransfer(dto, userId);
                } catch (Exception e) {
                    log.warn("Transfer failed: {}", e.getMessage());
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .join();

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        Account finalAccount = accountRepository.findByPublicId(accountId)
                .orElseThrow();

        assertThat(finalAccount.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        long successCount = testEventListener.getEvents().stream()
                .filter(e -> e.getStatus() == TransferStatus.COMPLETED)
                .count();

        assertThat(successCount).isLessThanOrEqualTo(6);

        BigDecimal expectedBalance = initialBalance.subtract(
                transferAmount.multiply(BigDecimal.valueOf(successCount))
        );
        assertThat(finalAccount.getBalance())
                .withFailMessage("Balance mismatch: expected=%s, actual=%s", expectedBalance, finalAccount.getBalance())
                .isEqualByComparingTo(expectedBalance);
    }
}