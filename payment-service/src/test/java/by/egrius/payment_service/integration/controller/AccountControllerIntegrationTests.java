package by.egrius.payment_service.integration.controller;

import by.egrius.payment_service.dto.account.AccountCreateDto;
import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.repository.AccountRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles(profiles = {"integration"})
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AccountControllerIntegrationTests extends BaseIntegrationTest {

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private OAuth2AuthorizedClientRepository authorizedClientRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    private final String BASE_URL = "/api/accounts";
    private String accessToken;

    private static final String TEST_NAME = "TestAccount";
    private static final String TEST_CURRENCY = "USD";

    @BeforeAll
    void setUp() {
        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                "/test/token",
                null,
                Map.class
        );

        if (tokenResponse.getBody() == null) {
            throw new RuntimeException("Token response body is null");
        }

        accessToken = (String) tokenResponse.getBody().get("access_token");
        System.out.println("✅ Token obtained: " + accessToken);
    }

    @BeforeEach
    void cleanDb() {
        accountRepository.deleteAll();
    }

    private boolean is2xxSuccessful(HttpStatusCode statusCode) {
        return statusCode.is2xxSuccessful();
    }

    @Nested
    class CreateAccountTests {

        @Test
        void shouldCreateAccount_ifPassedCorrectCredentials() {
            AccountCreateDto createDto = new AccountCreateDto(TEST_NAME, TEST_CURRENCY);

            ResponseEntity<AccountReadDto> response = sendCreateAccountRequest(createDto);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().name()).isEqualTo(TEST_NAME);
            assertThat(response.getBody().currency()).isEqualTo(TEST_CURRENCY);
            assertThat(response.getBody().balance()).isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(accountRepository.findByName(TEST_NAME)).isPresent();
        }

        @Test
        void shouldReturn400_ifNameIsEmpty() {
            AccountCreateDto createDto = new AccountCreateDto("", TEST_CURRENCY);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/create",
                    HttpMethod.POST,
                    createAuthRequest(createDto),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturn400_ifNameIsNull() {
            AccountCreateDto createDto = new AccountCreateDto(null, TEST_CURRENCY);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/create",
                    HttpMethod.POST,
                    createAuthRequest(createDto),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturn400_ifCurrencyIsInvalid() {
            AccountCreateDto createDto = new AccountCreateDto(TEST_NAME, "INVALID");

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/create",
                    HttpMethod.POST,
                    createAuthRequest(createDto),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldReturn401_ifNoTokenProvided() {
            AccountCreateDto createDto = new AccountCreateDto(TEST_NAME, TEST_CURRENCY);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<AccountCreateDto> request = new HttpEntity<>(createDto, headers);

            ResponseEntity<AccountReadDto> response = restTemplate.exchange(
                    BASE_URL + "/create",
                    HttpMethod.POST,
                    request,
                    AccountReadDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class GetAllAccountsTests {

        @BeforeEach
        void createTestAccounts() {
            accountRepository.deleteAll();

            for (int i = 0; i < 2; i++) {
                AccountCreateDto dto = new AccountCreateDto(TEST_NAME + "_" + i, TEST_CURRENCY);
                sendCreateAccountRequest(dto);
            }
        }

        @Test
        void shouldReturnAllUserAccounts() {
            ResponseEntity<List<AccountReadDto>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.GET,
                    createAuthRequest(),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).name()).startsWith(TEST_NAME);
        }

        @Test
        void shouldReturnEmptyList_ifNoAccounts() {
            accountRepository.deleteAll();

            assertThat(accountRepository.count()).isZero();

            ResponseEntity<List<AccountReadDto>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.GET,
                    createAuthRequest(),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        void shouldReturn401_ifNoTokenProvided() {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<List<AccountReadDto>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class GetAccountByIdTests {

        private UUID createdAccountPublicId;

        @BeforeEach
        void createTestAccount() {
            accountRepository.deleteAll();
            AccountCreateDto dto = new AccountCreateDto(TEST_NAME, TEST_CURRENCY);
            ResponseEntity<AccountReadDto> response = sendCreateAccountRequest(dto);
            createdAccountPublicId = response.getBody().publicId();
        }

        @Test
        void shouldReturnAccount_ifExistsAndUserOwnsIt() {
            ResponseEntity<AccountReadDto> response = restTemplate.exchange(
                    BASE_URL + "/" + createdAccountPublicId,
                    HttpMethod.GET,
                    createAuthRequest(),
                    AccountReadDto.class
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().publicId()).isEqualTo(createdAccountPublicId);
            assertThat(response.getBody().name()).isEqualTo(TEST_NAME);
        }

        @Test
        void shouldReturn404_ifAccountNotFound() {
            UUID nonExistentId = UUID.randomUUID();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + nonExistentId,
                    HttpMethod.GET,
                    createAuthRequest(),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldReturn401_ifNoTokenProvided() {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + createdAccountPublicId,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class DeleteAccountTests {

        private UUID createdAccountPublicId;

        @BeforeEach
        void createTestAccount() {
            accountRepository.deleteAll();
            AccountCreateDto dto = new AccountCreateDto(TEST_NAME, TEST_CURRENCY);
            ResponseEntity<AccountReadDto> response = sendCreateAccountRequest(dto);
            createdAccountPublicId = response.getBody().publicId();
        }

        @Test
        void shouldDeleteAccount_ifExistsAndUserOwnsIt() {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + createdAccountPublicId,
                    HttpMethod.DELETE,
                    createAuthRequest(),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(accountRepository.findByPublicId(createdAccountPublicId)).isEmpty();
        }

        @Test
        void shouldReturn404_ifAccountNotFound() {
            UUID nonExistentId = UUID.randomUUID();

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + nonExistentId,
                    HttpMethod.DELETE,
                    createAuthRequest(),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldReturn401_ifNoTokenProvided() {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/" + createdAccountPublicId,
                    HttpMethod.DELETE,
                    request,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<AccountReadDto> sendCreateAccountRequest(AccountCreateDto dto) {
        return restTemplate.exchange(
                BASE_URL + "/create",
                HttpMethod.POST,
                createAuthRequest(dto),
                AccountReadDto.class
        );
    }

    private HttpEntity<AccountCreateDto> createAuthRequest(AccountCreateDto dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return dto != null ? new HttpEntity<>(dto, headers) : new HttpEntity<>(headers);
    }

    private HttpEntity<Void> createAuthRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}