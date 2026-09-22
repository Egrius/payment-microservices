package by.egrius.payment_service.integration.service;

import by.egrius.payment_service.config.prod.WebConfig;
import by.egrius.payment_service.context.WebIntegrationTestContext;
import by.egrius.payment_service.dto.account.AccountCreateDto;
import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.IdempotencyKey;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.exception.ErrorCode;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.integration.config.TestCacheConfig;
import by.egrius.payment_service.integration.config.TestRabbitMQConfig;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.IdempotencyRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.service.AccountService;
import by.egrius.payment_service.service.TransferService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(
        classes = WebIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        TestCacheConfig.class,
        TestRabbitMQConfig.class,
        WebConfig.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TrasnferIdempotencyTests extends BaseIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID userPublicId = UUID.randomUUID();
    private final String username = "TestName";
    private AccountReadDto fromAccount;
    private AccountReadDto toAccount;

    private RequestPostProcessor currentUser(UUID userId) {
        return request -> {
            Map<String, Object> claims = Map.of(
                    "sub", userId.toString(),
                    "username", "TestName",
                    "email", "test@example.com",
                    "public_id", userId.toString()
            );

            Jwt jwt = new Jwt(
                    "mock-token-" + UUID.randomUUID(),
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("alg", "RS256", "typ", "JWT"),
                    claims
            );

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    jwt, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
            return request;
        };
    }

    @BeforeEach
    void setUp() {

        fromAccount = accountService.createAccount(userPublicId, new AccountCreateDto("Main Account", "USD"));
        toAccount = accountService.createAccount(userPublicId, new AccountCreateDto("Savings Account", "USD"));

        Account from = accountRepository.findByPublicIdAndUserId(fromAccount.publicId(), userPublicId).orElseThrow();
        from.setBalance(BigDecimal.valueOf(1000));
        accountRepository.save(from);
    }

    @AfterEach
    void clear() {
        idempotencyRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldCreateIdempotencyKeyInDB() {

        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(),
                toAccount.publicId(),
                BigDecimal.TEN
        );

        UUID idempotencyValue = UUID.randomUUID();

        TransferReadDto result = transferService.createTransfer(createDto, userPublicId, idempotencyValue);

        IdempotencyKey saved = idempotencyRepository
                .findByValueAndUserId(idempotencyValue, userPublicId)
                .orElseThrow();

        System.out.println(saved);

        assertThat(saved.getResponseStatus()).isEqualTo(201);
        assertThat(saved.getResponseBody()).isNotNull();
    }

    @Test
    void shouldSendTheSameAnswerForTheSameRequestAndDontDoubleDebit() throws Exception {
        BigDecimal amountToTransfer = BigDecimal.TEN;
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(), toAccount.publicId(), amountToTransfer
        );
        UUID idempotencyValue = UUID.randomUUID();

        // balances before the request
        BigDecimal fromBefore = accountRepository
                .findByPublicIdAndUserId(fromAccount.publicId(), userPublicId)
                .orElseThrow().getBalance();

        BigDecimal toBefore = accountRepository
                .findByPublicIdAndUserId(toAccount.publicId(), userPublicId)
                .orElseThrow().getBalance();

        // first request
        MvcResult firstResult = mockMvc.perform(post("/api/transfers")
                        .with(currentUser(userPublicId))
                        .header("Idempotency-Key", idempotencyValue.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        TransferReadDto first = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), TransferReadDto.class
        );

        // wait for a processing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Transfer t = transferRepository.findByTransferPublicId_and_UserPublicId(first.publicId(), userPublicId).orElseThrow();
            assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        });

        // Check the balances after the first request
        BigDecimal fromAfterFirst = accountRepository
                .findByPublicIdAndUserId(fromAccount.publicId(), userPublicId)
                .orElseThrow().getBalance();

        BigDecimal toAfterFirst = accountRepository
                .findByPublicIdAndUserId(toAccount.publicId(), userPublicId)
                .orElseThrow().getBalance();

        assertThat(fromAfterFirst).isEqualByComparingTo(fromBefore.subtract(amountToTransfer));
        assertThat(toAfterFirst).isEqualByComparingTo(toBefore.add(amountToTransfer));

        // retry
        MvcResult secondResult = mockMvc.perform(post("/api/transfers")
                        .with(currentUser(userPublicId))
                        .header("Idempotency-Key", idempotencyValue.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        TransferReadDto second = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(), TransferReadDto.class
        );

        // the same response
        assertThat(second.publicId()).isEqualTo(first.publicId());

        // Balances are the same after retry
        BigDecimal fromAfterSecond = accountRepository
                .findByPublicIdAndUserId(fromAccount.publicId(), userPublicId)
                .orElseThrow().getBalance();

        BigDecimal toAfterSecond = accountRepository
                .findByPublicIdAndUserId(toAccount.publicId(), userPublicId)
                .orElseThrow().getBalance();

        assertThat(fromAfterSecond).isEqualByComparingTo(fromAfterFirst);
        assertThat(toAfterSecond).isEqualByComparingTo(toAfterFirst);

        // there is only one transfer in DB
        long count = transferRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldReturn422WhenSameKeyButDifferentBody() throws Exception {
        UUID key = UUID.randomUUID();

        TransferCreateDto first = new TransferCreateDto(
                fromAccount.publicId(), toAccount.publicId(), BigDecimal.TEN
        );

        mockMvc.perform(post("/api/transfers")
                        .with(currentUser(userPublicId))
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        TransferCreateDto different = new TransferCreateDto(
                fromAccount.publicId(), toAccount.publicId(), BigDecimal.valueOf(20)
        );

        mockMvc.perform(post("/api/transfers")
                        .with(currentUser(userPublicId))
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(different)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCode.IDEMPOTENCY_KEY_MISMATCH.getCode()));
    }

    @Test
    void shouldReturnTheSameResponseForError() throws Exception {
        UUID key = UUID.randomUUID();

        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(), fromAccount.publicId(), BigDecimal.TEN
        );

        mockMvc.perform(post("/api/transfers")
                        .with(currentUser(userPublicId))
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SAME_ACCOUNT.getCode()));


        mockMvc.perform(post("/api/transfers")
                        .with(currentUser(userPublicId))
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SAME_ACCOUNT.getCode()));
    }

    @Test
    void shouldCreateOneTransferForConcurrentRequests() {

        int threadCount = 5;

        List<CompletableFuture<MvcResult>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        UUID key = UUID.randomUUID();

        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.publicId(), toAccount.publicId(), BigDecimal.TEN
        );

        for(int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await(20, TimeUnit.SECONDS);

                    return mockMvc.perform(post("/api/transfers")
                                    .with(currentUser(userPublicId))
                                    .header("Idempotency-Key", key.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(createDto)))
                        .andReturn();

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }

        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        executor.shutdown();

        assertThat(transferRepository.count()).isEqualTo(1);

        long created = futures.stream()
                .map(f -> {
                    try { return f.get(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                })
                .filter(s -> s.getResponse().getStatus() == 201)
                .count();

        assertThat(created).isEqualTo(1);
        assertThat(created).isLessThanOrEqualTo(threadCount);
    }

    @Test
    void shouldReturn400WhenIdempotencyKeyIsInvalid() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(
                fromAccount.publicId(), toAccount.publicId(), BigDecimal.TEN
        );

        mockMvc.perform(post("/api/transfers")
                        .with(currentUser(userPublicId))
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}