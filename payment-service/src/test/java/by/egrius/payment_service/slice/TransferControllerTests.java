package by.egrius.payment_service.slice;

import by.egrius.payment_service.argument_resolver.CurrentUserArgumentResolver;
import by.egrius.payment_service.controller.api.TransferController;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.exception.payment_service.TransferNotFoundException;
import by.egrius.payment_service.repository.filter.TransferFilter;
import by.egrius.payment_service.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = TransferController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
@Import({
        CurrentUserArgumentResolver.class
})
class TransferControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransferService transferService;

    private final UUID userId = UUID.randomUUID();
    private final UUID transferId = UUID.randomUUID();
    private final UUID fromAccountId = UUID.randomUUID();
    private final UUID toAccountId = UUID.randomUUID();
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
    void shouldCreateTransfer() throws Exception {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccountId,
                toAccountId,
                BigDecimal.valueOf(150L)
        );

        TransferReadDto responseDto = new TransferReadDto(
                transferId,
                fromAccountId,
                toAccountId,
                BigDecimal.valueOf(150L),
                TransferStatus.PENDING,
                LocalDateTime.now(),
                null,
                null
        );

        when(transferService.createTransfer(any(TransferCreateDto.class), eq(userId), any(UUID.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto))
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/transfers/" + transferId))
                .andExpect(jsonPath("$.publicId").value(transferId.toString()))
                .andExpect(jsonPath("$.fromAccountPublicId").value(fromAccountId.toString()))
                .andExpect(jsonPath("$.toAccountPublicId").value(toAccountId.toString()))
                .andExpect(jsonPath("$.amount").value(150))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturn400WhenFromAccountIsNull() throws Exception {
        TransferCreateDto invalidDto = new TransferCreateDto(
                null,
                toAccountId,
                BigDecimal.valueOf(150L)
        );

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenToAccountIsNull() throws Exception {
        TransferCreateDto invalidDto = new TransferCreateDto(
                fromAccountId,
                null,
                BigDecimal.valueOf(150L)
        );

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenAmountIsNegative() throws Exception {
        TransferCreateDto invalidDto = new TransferCreateDto(
                fromAccountId,
                toAccountId,
                BigDecimal.valueOf(-100L)
        );

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenAmountIsZero() throws Exception {
        TransferCreateDto invalidDto = new TransferCreateDto(
                fromAccountId,
                toAccountId,
                BigDecimal.ZERO
        );

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnTransferStatusById() throws Exception {
        TransferReadDto responseDto = new TransferReadDto(
                transferId,
                fromAccountId,
                toAccountId,
                BigDecimal.valueOf(150L),
                TransferStatus.COMPLETED,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now(),
                null
        );

        when(transferService.getTransferStatus(eq(transferId), eq(userId)))
                .thenReturn(responseDto);

        mockMvc.perform(get("/api/transfers/{transfer-public-id}", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(transferId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(150));
    }

    @Test
    void shouldReturn404WhenTransferNotFound() throws Exception {
        when(transferService.getTransferStatus(eq(transferId), eq(userId)))
                .thenThrow(new TransferNotFoundException(transferId));

        mockMvc.perform(get("/api/transfers/{transfer-public-id}", transferId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

// ---------- @ModelAttribute ----------

    @Test
    void shouldBindFilterFromQueryParams() throws Exception {
        when(transferService.getTransfersByFilter(any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/transfers")
                .param("status", "COMPLETED")
                .param("minAmount", "100")
                .param("maxAmount", "500"))
        .andExpect(status().isOk());

        ArgumentCaptor<TransferFilter> captor = ArgumentCaptor.forClass(TransferFilter.class);

        verify(transferService).getTransfersByFilter(any(), captor.capture(), any());

        TransferFilter filter = captor.getValue();

        assertThat(filter.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(filter.minAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(filter.maxAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void shouldPassNullFilterWhenNoParamsProvided() throws Exception {
        when(transferService.getTransfersByFilter(any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isOk());

        ArgumentCaptor<TransferFilter> captor = ArgumentCaptor.forClass(TransferFilter.class);

        verify(transferService).getTransfersByFilter(any(), captor.capture(), any());

        TransferFilter filter = captor.getValue();

        assertThat(filter.status()).isNull();
        assertThat(filter.fromDate()).isNull();
        assertThat(filter.toDate()).isNull();
        assertThat(filter.minAmount()).isNull();
        assertThat(filter.maxAmount()).isNull();
    }

    // ---------- @PageableDefault ----------

    @Test
    void shouldUseDefaultPageableWhenNotProvided() throws Exception {
        when(transferService.getTransfersByFilter(any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        verify(transferService).getTransfersByFilter(any(), any(), captor.capture());

        Pageable pageable = captor.getValue();

        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection().isDescending()).isTrue();

    }

    @Test
    void shouldUsePageableFromQueryParams() throws Exception {
        when(transferService.getTransfersByFilter(any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/transfers")
                        .param("page", "2")
                        .param("size", "50")
                        .param("sort", "amount,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferService).getTransfersByFilter(any(), any(), captor.capture());

        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().getOrderFor("amount").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void shouldBindMultipleSortParams() throws Exception {
        when(transferService.getTransfersByFilter(any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/transfers")
                        .param("sort", "amount,desc")
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferService).getTransfersByFilter(any(), any(), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertThat(sort.getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    // ---------- JSON ----------

    @Test
    void shouldReturnPageContentInJson() throws Exception {
        TransferReadDto dto = new TransferReadDto(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, TransferStatus.COMPLETED,
                LocalDateTime.now(), null, null
        );
        Page<TransferReadDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
        when(transferService.getTransfersByFilter(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].publicId").value(dto.publicId().toString()))
                .andExpect(jsonPath("$.content[0].amount").value(10));
    }

    @Test
    void shouldReturnPaginationMetadata() throws Exception {
        TransferReadDto dto = new TransferReadDto(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, TransferStatus.COMPLETED,
                LocalDateTime.now(), null, null
        );

        Page<TransferReadDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 100);
        when(transferService.getTransfersByFilter(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(100))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void shouldReturnEmptyPageWhenNoTransfers() throws Exception {
        when(transferService.getTransfersByFilter(any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}