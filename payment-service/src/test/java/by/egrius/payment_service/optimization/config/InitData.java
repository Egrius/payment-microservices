package by.egrius.payment_service.optimization.config;

import by.egrius.payment_service.context.ServiceIntegrationTestContext;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(classes = ServiceIntegrationTestContext.class)
@Slf4j
public class InitData extends BaseOptimizingTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    private static final Random random = new Random();

    private static final int TOTAL_ACCOUNTS = 200_000;
    private static final int TOTAL_TRANSFERS = 2_000_000;
    private static final int BATCH_SIZE = 1000;
    private static final int PROGRESS_LOG_INTERVAL = 100_000;

    private List<Account> allAccounts = new ArrayList<>();

    @Test
    void cleanUp() {

        transferRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void init() {
        long startTime = System.currentTimeMillis();
        log.info("🚀 Starting data generation...");
        log.info("📊 Target: {} accounts, {} transfers", TOTAL_ACCOUNTS, TOTAL_TRANSFERS);

        generateAccounts();

        generateTransfers();

        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ Data generation completed in {} ms ({} seconds)!", duration, duration / 1000);
        log.info("📊 Accounts: {}, Transfers: {}",
                accountRepository.count(),
                transferRepository.count()
        );
    }

    private void generateAccounts() {
        log.info("💰 Generating {} accounts...", TOTAL_ACCOUNTS);
        long startTime = System.currentTimeMillis();

        List<Account> batchInsert = new ArrayList<>(BATCH_SIZE);
        AtomicInteger progressCounter = new AtomicInteger(0);

        for (int i = 0; i < TOTAL_ACCOUNTS; i++) {
            Account account = Account.builder()
                    .publicId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .balance(BigDecimal.valueOf(1000 + random.nextDouble() * 9000))
                    .currency("US")
                    .name("Account_" + i)
                    .createdAt(LocalDateTime.now().minusDays(random.nextInt(365)))
                    .build();

            batchInsert.add(account);

            if (batchInsert.size() >= BATCH_SIZE) {
                accountRepository.saveAll(batchInsert);
                allAccounts.addAll(batchInsert);
                batchInsert.clear();

                int current = progressCounter.addAndGet(BATCH_SIZE);
                if (current % (BATCH_SIZE * 10) == 0) {
                    log.info("   Accounts progress: {} / {} ({}%)",
                            current, TOTAL_ACCOUNTS,
                            (current * 100) / TOTAL_ACCOUNTS);
                }
            }
        }

        if (!batchInsert.isEmpty()) {
            accountRepository.saveAll(batchInsert);
            allAccounts.addAll(batchInsert);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ Generated {} accounts in {} ms", allAccounts.size(), duration);
    }

    private void generateTransfers() {
        log.info("💸 Generating {} transfers...", TOTAL_TRANSFERS);
        long startTime = System.currentTimeMillis();

        List<Transfer> batchInsert = new ArrayList<>(BATCH_SIZE);
        int successfulTransfers = 0;

        for (int i = 0; i < TOTAL_TRANSFERS; i++) {
            Account fromAccount = getRandomAccount();
            Account toAccount = getRandomAccount();

            int attempts = 0;
            while (fromAccount.getId().equals(toAccount.getId()) && attempts < 10) {
                toAccount = getRandomAccount();
                attempts++;
            }

            if (fromAccount.getId().equals(toAccount.getId())) {
                continue;
            }

            BigDecimal amount = BigDecimal.valueOf(10 + random.nextDouble() * 990);
            TransferStatus status = getRandomStatus();

            LocalDateTime createdAt = LocalDateTime.now()
                    .minusDays(random.nextInt(90))
                    .minusHours(random.nextInt(24))
                    .minusMinutes(random.nextInt(60));

            Transfer transfer = Transfer.builder()
                    .publicId(UUID.randomUUID())
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .amount(amount)
                    .status(status)
                    .createdAt(createdAt)
                    .processedAt(status == TransferStatus.COMPLETED ? createdAt.plusMinutes(random.nextInt(10)) : null)
                    .build();

            batchInsert.add(transfer);
            successfulTransfers++;

            if (batchInsert.size() >= BATCH_SIZE) {
                transferRepository.saveAll(batchInsert);
                batchInsert.clear();

                if (successfulTransfers % PROGRESS_LOG_INTERVAL == 0) {
                    log.info("   Transfers progress: {} / {} ({}%)",
                            successfulTransfers, TOTAL_TRANSFERS,
                            (successfulTransfers * 100) / TOTAL_TRANSFERS);
                }
            }
        }

        if (!batchInsert.isEmpty()) {
            transferRepository.saveAll(batchInsert);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ Generated {} transfers in {} ms", successfulTransfers, duration);
    }

    private Account getRandomAccount() {
        return allAccounts.get(random.nextInt(allAccounts.size()));
    }

    private TransferStatus getRandomStatus() {
        double rand = random.nextDouble();
        if (rand < 0.80) {
            return TransferStatus.COMPLETED;
        } else if (rand < 0.90) {
            return TransferStatus.PENDING;
        } else {
            return TransferStatus.FAILED;
        }
    }
}