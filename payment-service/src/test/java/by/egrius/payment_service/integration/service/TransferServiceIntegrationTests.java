package by.egrius.payment_service.integration.service;

import by.egrius.payment_service.dto.account.AccountCreateDto;
import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.context.ServiceIntegrationTestContext;
import by.egrius.payment_service.exception.payment_service.SameAccountTransferException;
import by.egrius.payment_service.exception.payment_service.TransferNotFoundException;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.integration.config.TestCacheConfig;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.service.AccountService;
import by.egrius.payment_service.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;


@ActiveProfiles("test")
@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(TestCacheConfig.class)
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


    @Test
    void shouldThrowExceptionWhenFromAndToAccountsAreSame() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                fromAccount.publicId(),
                BigDecimal.TEN
        );

        assertThatThrownBy(() -> transferService.createTransfer(createDto, userPublicId))
                .isInstanceOf(SameAccountTransferException.class);
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
    void shouldThrowExceptionWhenTransferNotFound() {
        UUID randomId = UUID.randomUUID();
        assertThatThrownBy(() -> transferService.getTransferStatus(randomId, userPublicId))
                .isInstanceOf(TransferNotFoundException.class);
    }
}