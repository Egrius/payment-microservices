package by.egrius.payment_service.integration.service;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.mapper.TransferMapper;
import by.egrius.payment_service.mapper.TransferMapperImpl;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.repository.filter.TransferFilter;
import by.egrius.payment_service.service.CacheService;
import by.egrius.payment_service.service.IdempotencyService;
import by.egrius.payment_service.service.TransferService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Import({TransferService.class, TransferMapperImpl.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TransferServicePagingTests extends BaseIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferMapper transferMapper;

    @MockitoBean
    private IdempotencyService idempotencyService;

    @MockitoBean
    private CacheService cacheService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean(name = "hashObjectMapper")
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    private final UUID userId = UUID.randomUUID();

    private final int randomTransfers = 150;

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

        List<Transfer> batch = new ArrayList<>();
        int batchSize = 30;

        for(int i = 0; i < randomTransfers; i++) {

            Account from = (i % 2 == 0) ? userAccount : anotherAccount;
            Account to = (i % 2 == 0) ? anotherAccount : userAccount;

            BigDecimal amount = BigDecimal.valueOf(10 + i * 5);

            Transfer transfer = Transfer.builder()
                    .fromAccount(from)
                    .toAccount(to)
                    .amount(amount)
                    .status(TransferStatus.COMPLETED)
                    .build();

            batch.add(transfer);

            if(i % batchSize == 0) {
                transferRepository.saveAll(batch);
                batch.clear();
            }
        }

        if(!batch.isEmpty()) transferRepository.saveAll(batch); batch.clear();

        assertThat(transferRepository.count()).isEqualTo(randomTransfers);
    }

    @Test
    void shouldReturnCorrectPageResponse() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(new Sort.Order(Sort.Direction.DESC, "amount")));

        TransferFilter filter = new TransferFilter(
                null, null, null, BigDecimal.valueOf(100L), null);

        Page<TransferReadDto> page = transferService.getTransfersByFilter(userId, filter, pageable);

        assertThat(page.getNumberOfElements()).isEqualTo(20);

        assertThat(page.getContent()).allMatch(dto -> dto != null);

        assertThat(page.getContent())
                .extracting(TransferReadDto::amount)
                .allMatch(amount -> amount.compareTo(BigDecimal.valueOf(100)) >= 0);
    }

    @Test
    void shouldReturnCorrectPages() {
        TransferFilter filter = new TransferFilter(
                null, null, null, null, null);

        Pageable page0 = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount"));
        Pageable page1 = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "amount"));
        Pageable lastPage = PageRequest.of(7, 20, Sort.by(Sort.Direction.DESC, "amount"));

        Page<TransferReadDto> first = transferService.getTransfersByFilter(userId, filter, page0);
        Page<TransferReadDto> second = transferService.getTransfersByFilter(userId, filter, page1);
        Page<TransferReadDto> last = transferService.getTransfersByFilter(userId, filter, lastPage);

        assertThat(first.getContent()).hasSize(20);
        assertThat(second.getContent()).hasSize(20);
        assertThat(last.getContent()).hasSize(10);
        assertThat(first.getTotalPages()).isEqualTo(8);
    }

    @Test
    void shouldSortByAmountDesc() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount"));

        TransferFilter filter = new TransferFilter(
                null, null, null, null, null);

        Page<TransferReadDto> page = transferService.getTransfersByFilter(userId, filter, pageable);

        assertThat(page.getContent())
                .extracting(TransferReadDto::amount)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }
}
