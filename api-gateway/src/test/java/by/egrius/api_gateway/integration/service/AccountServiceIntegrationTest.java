package by.egrius.api_gateway.integration.service;

import by.egrius.api_gateway.context.ServiceIntegrationTestContext;
import by.egrius.api_gateway.dto.account.AccountCreateDto;
import by.egrius.api_gateway.dto.account.AccountReadDto;
import by.egrius.api_gateway.entity.Account;
import by.egrius.api_gateway.exception.ResourceNotFoundException;
import by.egrius.api_gateway.integration.config.BaseIntegrationTest;
import by.egrius.api_gateway.repository.AccountRepository;
import by.egrius.api_gateway.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Transactional
class AccountServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private UUID userPublicId;
    private AccountCreateDto createDto;

    @BeforeEach
    void setUp() {
        userPublicId = UUID.randomUUID();
        createDto = new AccountCreateDto("Main Account", "USD");
        accountRepository.deleteAll();
    }

    @Test
    void createAccount_ShouldPersistAndReturnAccount() {

        AccountReadDto result = accountService.createAccount(userPublicId, createDto);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Main Account");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.ZERO);

        Account saved = accountRepository.findByPublicIdAndUserId(result.publicId(), userPublicId).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Main Account");
    }

    @Test
    void getAccountByPublicId_WhenExists_ShouldReturnAccount() {

        AccountReadDto created = accountService.createAccount(userPublicId, createDto);


        AccountReadDto result = accountService.getAccountByPublicId(created.publicId(), userPublicId);


        assertThat(result).isNotNull();
        assertThat(result.publicId()).isEqualTo(created.publicId());
        assertThat(result.name()).isEqualTo(created.name());
    }

    @Test
    void getAccountByPublicId_WhenNotFound_ShouldThrowResourceNotFoundException() {

        UUID nonExistentId = UUID.randomUUID();


        assertThatThrownBy(() -> accountService.getAccountByPublicId(nonExistentId, userPublicId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    void getAllAccountsByUserId_ShouldReturnOnlyUserAccounts() {

        UUID anotherUser = UUID.randomUUID();
        accountService.createAccount(userPublicId, createDto);
        accountService.createAccount(userPublicId, new AccountCreateDto("Second", "EUR"));
        accountService.createAccount(anotherUser, new AccountCreateDto("Other", "USD"));


        List<AccountReadDto> result = accountService.getAllAccountsByUserId(userPublicId);


        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountReadDto::name)
                .containsExactlyInAnyOrder("Main Account", "Second");
    }

    @Test
    void deleteAccount_WhenExists_ShouldRemoveFromDb() {

        AccountReadDto created = accountService.createAccount(userPublicId, createDto);


        accountService.deleteAccount(created.publicId(), userPublicId);


        assertThat(accountRepository.findByPublicIdAndUserId(created.publicId(), userPublicId))
                .isEmpty();
    }

    @Test
    void deleteAccount_WhenNotFound_ShouldThrowResourceNotFoundException() {

        UUID nonExistentId = UUID.randomUUID();


        assertThatThrownBy(() -> accountService.deleteAccount(nonExistentId, userPublicId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}