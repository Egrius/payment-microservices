package by.egrius.payment_service.integration.service;

import by.egrius.payment_service.context.ServiceIntegrationTestContext;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.exception.payment_service.AccountNotFoundException;
import by.egrius.payment_service.exception.payment_service.InsufficientFundsException;
import by.egrius.payment_service.exception.payment_service.SameAccountTransferException;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.service.TransferService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Slf4j
@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(TestEventListener.class)
@Transactional
public class TransferRollbackTest extends BaseIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TestEventListener testEventListener;

    private UUID userPublicId;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        testEventListener.reset();
        userPublicId = UUID.randomUUID();

        // Создаем аккаунты с начальными балансами
        fromAccount = Account.builder()
                .publicId(UUID.randomUUID())
                .userId(userPublicId)
                .balance(BigDecimal.valueOf(1000))
                .currency("USD")
                .name("Test_From")
                .build();

        toAccount = Account.builder()
                .publicId(UUID.randomUUID())
                .userId(userPublicId)
                .balance(BigDecimal.valueOf(500))
                .currency("USD")
                .name("Test_To")
                .build();

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }

    @Test
    void testRollback_WhenSameAccount() {
        TransferCreateDto dto = new TransferCreateDto(
                fromAccount.getPublicId(),
                fromAccount.getPublicId(),  // Тот же счет
                BigDecimal.valueOf(100)
        );

        assertThatThrownBy(() -> {
            transferService.createTransfer(dto, userPublicId);
        })
                .isInstanceOf(SameAccountTransferException.class)
                .hasMessageContaining("Cannot transfer to the same account");

        // Балансы не изменились
        Account updated = accountRepository.findById(fromAccount.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }


    // 6. Тест: Откат при несуществующем счете
    @Test
    void testRollback_WhenAccountNotFound() {
        UUID nonExistentId = UUID.randomUUID();

        TransferCreateDto dto = new TransferCreateDto(
                nonExistentId,
                toAccount.getPublicId(),
                BigDecimal.valueOf(100)
        );

        assertThatThrownBy(() -> {
            transferService.createTransfer(dto, userPublicId);
        }).isInstanceOf(AccountNotFoundException.class);

        Account updatedFrom = accountRepository.findById(fromAccount.getId()).orElseThrow();
        Account updatedTo = accountRepository.findById(toAccount.getId()).orElseThrow();

        assertThat(updatedFrom.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(updatedTo.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }
}
