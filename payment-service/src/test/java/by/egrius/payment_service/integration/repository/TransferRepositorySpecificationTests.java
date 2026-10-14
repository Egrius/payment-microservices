package by.egrius.payment_service.integration.repository;

import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.repository.specification.TransferSpecifications;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TransferRepositorySpecificationTests extends BaseIntegrationTest {

    private final UUID userId = UUID.randomUUID();

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @BeforeAll
    void setUp() {
        Account userAccount = Account.builder()
                .userId(userId)
                .balance(BigDecimal.TEN)
                .currency("BYN")
                .name("User_Account")
                .build();

        Account anotherAccount = Account.builder()
                .userId(UUID.randomUUID())
                .balance(BigDecimal.TEN)
                .currency("BYN")
                .name("AnotherUser_Account")
                .build();

        accountRepository.save(userAccount);
        accountRepository.save(anotherAccount);

        Transfer userTransfer = Transfer.builder()
                .fromAccount(userAccount)
                .toAccount(anotherAccount)
                .amount(BigDecimal.valueOf(100L))
                .status(TransferStatus.COMPLETED)
                .build();

        Transfer userTransferFailed = Transfer.builder()
                .fromAccount(userAccount)
                .toAccount(anotherAccount)
                .amount(BigDecimal.valueOf(200L))
                .status(TransferStatus.FAILED)
                .build();

        Transfer anotherTransfer = Transfer.builder()
                .fromAccount(anotherAccount)
                .toAccount(userAccount)
                .amount(BigDecimal.valueOf(300L))
                .status(TransferStatus.COMPLETED)
                .build();

        transferRepository.save(userTransfer);
        transferRepository.save(userTransferFailed);
        transferRepository.save(anotherTransfer);

    }

    @Test
    void byUser_shouldReturnOnlyUserTransfers() {

        Specification<Transfer> spec = Specification.where(TransferSpecifications.byUser(userId));

        List<Transfer> transferList = transferRepository.findAll(spec);

        assertThat(transferList).hasSize(3);

    }

    @Test
    void byStatus_shouldFilterByStatus() {

        Specification<Transfer> spec = Specification.where(TransferSpecifications.byStatus(TransferStatus.COMPLETED));

        List<Transfer> transferList = transferRepository.findAll(spec);

        assertThat(transferList).hasSize(2);

        assertEquals(TransferStatus.COMPLETED, transferList.get(0).getStatus());
        assertEquals(TransferStatus.COMPLETED, transferList.get(1).getStatus());
    }

    @Test
    void byAmountRange_shouldFilterByMinAndMax() {

        Specification<Transfer> spec = Specification
                .where(TransferSpecifications.byMinAmount(BigDecimal.valueOf(150L)))
                .and(TransferSpecifications.byMaxAmount(BigDecimal.valueOf(250L)));

        List<Transfer> transferList = transferRepository.findAll(spec);

        assertThat(transferList).hasSize(1);

        assertThat(transferList.getFirst().getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(200L));
    }

    @Test
    void composition_shouldApplyAllFilters() {

        Specification<Transfer> spec = Specification
                .where(TransferSpecifications.byMinAmount(BigDecimal.valueOf(150L)))
                .and(TransferSpecifications.byUser(userId))
                .and(TransferSpecifications.byStatus(TransferStatus.COMPLETED));

        List<Transfer> transferList = transferRepository.findAll(spec);

        assertThat(transferList).hasSize(1);
    }
}