package by.egrius.payment_service.slice;

import by.egrius.payment_service.argument_resolver.CurrentUserArgumentResolver;
import by.egrius.payment_service.controller.api.TransferController;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.exception.payment_service.TransferNotFoundException;
import by.egrius.payment_service.service.TransferService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        when(transferService.createTransfer(any(TransferCreateDto.class), eq(userId)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/transfers/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
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

        mockMvc.perform(post("/api/transfers/create")
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

        mockMvc.perform(post("/api/transfers/create")
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

        mockMvc.perform(post("/api/transfers/create")
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

        mockMvc.perform(post("/api/transfers/create")
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
}