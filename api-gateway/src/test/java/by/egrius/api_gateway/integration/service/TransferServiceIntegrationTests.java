package by.egrius.api_gateway.integration.service;

import by.egrius.api_gateway.dto.account.AccountCreateDto;
import by.egrius.api_gateway.dto.account.AccountReadDto;
import by.egrius.api_gateway.dto.transfer.TransferCreateDto;
import by.egrius.api_gateway.dto.transfer.TransferReadDto;
import by.egrius.api_gateway.entity.Account;
import by.egrius.api_gateway.entity.TransferStatus;
import by.egrius.api_gateway.context.ServiceIntegrationTestContext;
import by.egrius.api_gateway.integration.config.BaseIntegrationTest;
import by.egrius.api_gateway.repository.AccountRepository;
import by.egrius.api_gateway.service.AccountService;
import by.egrius.api_gateway.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;


@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class TransferServiceIntegrationTests extends BaseIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private UUID userPublicId;
    private AccountReadDto fromAccount;
    private AccountReadDto toAccount;

    @BeforeEach
    void setUp() {
        userPublicId = UUID.randomUUID();
        fromAccount = accountService.createAccount(userPublicId, new AccountCreateDto("Main Account", "USD"));
        toAccount = accountService.createAccount(userPublicId, new AccountCreateDto("Savings Account", "USD"));

        Account from = accountRepository.findByPublicIdAndUserId(fromAccount.publicId(), userPublicId).orElseThrow();
        from.setBalance(BigDecimal.valueOf(1000));
        accountRepository.save(from);
    }

    private TransferReadDto waitForTransferProcessing(UUID transferId, UUID userId, int maxAttempts, long sleepMillis) {
        TransferReadDto status = null;
        for (int i = 0; i < maxAttempts; i++) {
            status = transferService.getTransferStatus(transferId, userId);
            if (status.status() != TransferStatus.PENDING) {
                break;
            }
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return status;
    }

    @Test
    void shouldCreateTransferWithPendingStatusAndThenComplete() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.valueOf(100)
        );

        TransferReadDto created = transferService.createTransfer(createDto, userPublicId);
        assertThat(created.status()).isEqualTo(TransferStatus.PENDING);

        TransferReadDto updated = waitForTransferProcessing(created.transferId(), userPublicId, 50, 100);
        assertThat(updated.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(updated.processedAt()).isNotNull();

        Account from = accountRepository.findByPublicIdAndUserId(fromAccount.publicId(), userPublicId).orElseThrow();
        Account to = accountRepository.findByPublicIdAndUserId(toAccount.publicId(), userPublicId).orElseThrow();
        assertThat(from.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(900));
        assertThat(to.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void shouldFailTransferWhenInsufficientFunds() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.valueOf(2000)
        );

        TransferReadDto created = transferService.createTransfer(createDto, userPublicId);
        assertThat(created.status()).isEqualTo(TransferStatus.PENDING);

        TransferReadDto updated = waitForTransferProcessing(created.transferId(), userPublicId, 50, 100);
        assertThat(updated.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(updated.reason()).contains("Insufficient funds");

        Account from = accountRepository.findByPublicIdAndUserId(fromAccount.publicId(), userPublicId).orElseThrow();
        Account to = accountRepository.findByPublicIdAndUserId(toAccount.publicId(), userPublicId).orElseThrow();
        assertThat(from.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(to.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldThrowExceptionWhenFromAndToAccountsAreSame() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                fromAccount.publicId(),
                BigDecimal.TEN
        );

        assertThatThrownBy(() -> transferService.createTransfer(createDto, userPublicId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Can't create transfer for the same account");
    }

    @Test
    void shouldThrowExceptionWhenFromAccountNotBelongToUser() {
        UUID otherUserId = UUID.randomUUID();
        AccountReadDto otherAccount = accountService.createAccount(otherUserId, new AccountCreateDto("Other", "USD"));

        TransferCreateDto createDto = new TransferCreateDto(
                otherAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.TEN
        );

        assertThatThrownBy(() -> transferService.createTransfer(createDto, userPublicId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("'from account' does not exist");
    }

    @Test
    void shouldThrowExceptionWhenToAccountNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                nonExistentId,
                BigDecimal.TEN
        );

        assertThatThrownBy(() -> transferService.createTransfer(createDto, userPublicId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("'to account' does not exist");
    }

    @Test
    void shouldGetTransferStatus() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.valueOf(50)
        );
        TransferReadDto created = transferService.createTransfer(createDto, userPublicId);

        TransferReadDto status = transferService.getTransferStatus(created.transferId(), userPublicId);
        assertThat(status.transferId()).isEqualTo(created.transferId());
        assertThat(status.status()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    void shouldThrowExceptionWhenTransferNotFound() {
        UUID randomId = UUID.randomUUID();
        assertThatThrownBy(() -> transferService.getTransferStatus(randomId, userPublicId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Couldn't find a transfer with id");
    }
}