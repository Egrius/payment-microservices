package by.egrius.api_gateway.integration.controller;

import by.egrius.api_gateway.dto.account.AccountCreateDto;
import by.egrius.api_gateway.dto.account.AccountReadDto;
import by.egrius.api_gateway.dto.transfer.TransferCreateDto;
import by.egrius.api_gateway.dto.transfer.TransferReadDto;
import by.egrius.api_gateway.entity.TransferStatus;
import by.egrius.api_gateway.integration.config.BaseIntegrationTest;
import by.egrius.api_gateway.repository.AccountRepository;
import by.egrius.api_gateway.repository.TransferRepository;
import by.egrius.api_gateway.service.AccountService;
import by.egrius.api_gateway.service.TransferService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles(profiles = {"integration"})
@AutoConfigureTestRestTemplate
class TransferControllerIntegrationTests extends BaseIntegrationTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private OAuth2AuthorizedClientRepository authorizedClientRepository;


    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    private UUID userPublicId;
    private AccountReadDto fromAccount;
    private AccountReadDto toAccount;
    private String accessToken;

    private static final String BASE_URL = "/api/transfers";

    @BeforeAll
    void setUpToken() {
        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                "/test/token",
                null,
                Map.class
        );
        accessToken = (String) tokenResponse.getBody().get("access_token");
    }

    @BeforeEach
    void setUpAccounts() {
        userPublicId = UUID.fromString("ae22624a-06f6-428c-afec-893fb3b3f448");

        transferRepository.deleteAll();
        accountRepository.deleteAll();

        fromAccount = accountService.createAccount(userPublicId, new AccountCreateDto("Main Account", "USD"));
        toAccount = accountService.createAccount(userPublicId, new AccountCreateDto("Savings Account", "USD"));

        var from = accountRepository.findByPublicIdAndUserId(fromAccount.publicId(), userPublicId).orElseThrow();
        from.setBalance(BigDecimal.valueOf(1000));
        accountRepository.save(from);
    }
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    void shouldCreateTransfer() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.valueOf(100)
        );

        HttpEntity<TransferCreateDto> request = new HttpEntity<>(createDto, createAuthHeaders());

        ResponseEntity<TransferReadDto> response = restTemplate.exchange(
                BASE_URL + "/create",
                HttpMethod.POST,
                request,
                TransferReadDto.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(response.getBody().amount()).isEqualByComparingTo(BigDecimal.valueOf(100));

        await().untilAsserted(() -> {
            TransferReadDto updated = transferService.getTransferStatus(
                    response.getBody().publicId(),
                    userPublicId
            );
            assertThat(updated.status()).isEqualTo(TransferStatus.COMPLETED);
        });

        var from = accountRepository.findByPublicIdAndUserId(fromAccount.publicId(), userPublicId).orElseThrow();
        var to = accountRepository.findByPublicIdAndUserId(toAccount.publicId(), userPublicId).orElseThrow();
        assertThat(from.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(900));
        assertThat(to.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void shouldReturn400WhenInsufficientFunds() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.valueOf(2000)
        );

        HttpEntity<TransferCreateDto> request = new HttpEntity<>(createDto, createAuthHeaders());

        ResponseEntity<TransferReadDto> response = restTemplate.exchange(
                BASE_URL + "/create",
                HttpMethod.POST,
                request,
                TransferReadDto.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        await().untilAsserted(() -> {
            TransferReadDto updated = transferService.getTransferStatus(
                    response.getBody().publicId(),
                    userPublicId
            );
            assertThat(updated.status()).isEqualTo(TransferStatus.FAILED);
            assertThat(updated.reason()).contains("Insufficient funds");
        });
    }

    @Test
    void shouldReturn400WhenFromAndToAccountsAreSame() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                fromAccount.publicId(),
                BigDecimal.TEN
        );

        HttpEntity<TransferCreateDto> request = new HttpEntity<>(createDto, createAuthHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/create",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void shouldReturn404WhenFromAccountNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        TransferCreateDto createDto = new TransferCreateDto(
                nonExistentId,
                toAccount.publicId(),
                BigDecimal.TEN
        );

        HttpEntity<TransferCreateDto> request = new HttpEntity<>(createDto, createAuthHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/create",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void shouldReturn404WhenToAccountNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                nonExistentId,
                BigDecimal.TEN
        );

        HttpEntity<TransferCreateDto> request = new HttpEntity<>(createDto, createAuthHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/create",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void shouldGetTransferStatus() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.valueOf(50)
        );
        var created = transferService.createTransfer(createDto, userPublicId);

        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders());

        ResponseEntity<TransferReadDto> response = restTemplate.exchange(
                BASE_URL + "/" + created.publicId(),
                HttpMethod.GET,
                request,
                TransferReadDto.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().publicId()).isEqualTo(created.publicId());
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    void shouldReturn404WhenTransferNotFound() {
        UUID randomId = UUID.randomUUID();

        HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/" + randomId,
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturn401WhenNoToken() {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.TEN
        );

        HttpEntity<TransferCreateDto> request = new HttpEntity<>(createDto, new HttpHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/create",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
}