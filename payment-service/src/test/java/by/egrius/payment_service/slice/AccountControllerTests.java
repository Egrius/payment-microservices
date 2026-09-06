package by.egrius.payment_service.slice;

import by.egrius.payment_service.argument_resolver.CurrentUserArgumentResolver;
import by.egrius.payment_service.controller.api.AccountController;
import by.egrius.payment_service.dto.account.AccountCreateDto;
import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.exception.payment_service.AccountNotFoundException;
import by.egrius.payment_service.exception.payment_service.AccountAccessDeniedException;
import by.egrius.payment_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AccountController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        })
@Import(CurrentUserArgumentResolver.class)
class AccountControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final String username = "Test";

    @BeforeEach
    void setUp() {
        Map<String, Object> claims = Map.of(
                "sub", userId.toString(),
                "username", username,
                "email", "test@example.com",
                "public_id", userId.toString()
        );

        Map<String, Object> headers = Map.of(
                "alg", "RS256",
                "typ", "JWT"
        );

        Jwt jwt = new Jwt(
                "mock-token-" + UUID.randomUUID(),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                headers,
                claims
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                jwt,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldReturnAllUserAccounts() throws Exception {
        AccountReadDto account1 = new AccountReadDto(accountId, "Main", "USD", BigDecimal.ZERO);
        AccountReadDto account2 = new AccountReadDto(UUID.randomUUID(), "Savings", "EUR", BigDecimal.valueOf(100));

        when(accountService.getAllAccountsByUserId(any(UUID.class)))
                .thenReturn(List.of(account1, account2));

        mockMvc.perform(get("/api/accounts")
                        .requestAttr("currentUser", userId)) // или через SecurityContext
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].publicId").value(accountId.toString()))
                .andExpect(jsonPath("$[0].name").value("Main"));
    }

    @Test
    void shouldCreateAccount() throws Exception {
        AccountCreateDto createDto = new AccountCreateDto("New Account", "USD");
        AccountReadDto responseDto = new AccountReadDto(accountId, "New Account", "USD", BigDecimal.ZERO);

        when(accountService.createAccount(any(UUID.class), any(AccountCreateDto.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/accounts/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto))
                        .requestAttr("currentUser", userId))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/accounts/" + accountId))
                .andExpect(jsonPath("$.publicId").value(accountId.toString()))
                .andExpect(jsonPath("$.name").value("New Account"));
    }

    @Test
    void shouldReturn400WhenCreateAccountWithInvalidData() throws Exception {
        AccountCreateDto invalidDto = new AccountCreateDto("", ""); // пустые поля

        mockMvc.perform(post("/api/accounts/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto))
                        .requestAttr("currentUser", userId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnAccountById() throws Exception {
        AccountReadDto accountDto = new AccountReadDto(accountId, "Main", "USD", BigDecimal.ZERO);

        when(accountService.getAccountByPublicId(eq(accountId), any(UUID.class)))
                .thenReturn(accountDto);

        mockMvc.perform(get("/api/accounts/{public-account-id}", accountId)
                        .requestAttr("currentUser", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(accountId.toString()))
                .andExpect(jsonPath("$.name").value("Main"));
    }

    @Test
    void shouldReturn404WhenAccountNotFound() throws Exception {
        when(accountService.getAccountByPublicId(eq(accountId), any(UUID.class)))
                .thenThrow(new AccountNotFoundException(accountId));

        mockMvc.perform(get("/api/accounts/{public-account-id}", accountId)
                        .requestAttr("currentUser", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenAccessDenied() throws Exception {
        when(accountService.getAccountByPublicId(eq(accountId), any(UUID.class)))
                .thenThrow(new AccountAccessDeniedException(accountId, userId));

        mockMvc.perform(get("/api/accounts/{public-account-id}", accountId)
                        .requestAttr("currentUser", userId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldDeleteAccount() throws Exception {
        mockMvc.perform(delete("/api/accounts/{public-account-id}", accountId)
                        .requestAttr("currentUser", userId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentAccount() throws Exception {
        doThrow(new AccountNotFoundException(accountId))
                .when(accountService).deleteAccount(eq(accountId), any(UUID.class));

        mockMvc.perform(delete("/api/accounts/{public-account-id}", accountId)
                        .requestAttr("currentUser", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenDeletingAccountWithoutAccess() throws Exception {
        doThrow(new AccountAccessDeniedException(accountId, userId))
                .when(accountService).deleteAccount(eq(accountId), any(UUID.class));

        mockMvc.perform(delete("/api/accounts/{public-account-id}", accountId)
                        .requestAttr("currentUser", userId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}