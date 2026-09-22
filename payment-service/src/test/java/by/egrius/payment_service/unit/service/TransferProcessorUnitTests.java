package by.egrius.payment_service.unit.service;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.event.TransferAddedEvent;
import by.egrius.payment_service.mapper.TransferMapper;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.service.CacheService;
import by.egrius.payment_service.service.TransferFailureHandler;
import by.egrius.payment_service.service.TransferProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferProcessorUnitTests {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferMapper transferMapper;

    @Mock
    private CacheService cacheService;

    @Mock
    private TransferFailureHandler transferFailureHandler;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private TransferProcessor transferProcessor;

    private static final long FROM_ACCOUNT_ID = 1L;
    private static final long TO_ACCOUNT_ID = 2L;
    private static final long TRANSFER_ID = 100L;

    private UUID userId;
    private Account fromAccount;
    private Account toAccount;
    private Transfer transfer;
    private TransferAddedEvent transferAddedEvent;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        fromAccount = Account.builder()
                .id(FROM_ACCOUNT_ID)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(1000))
                .userId(userId)
                .build();

        toAccount = Account.builder()
                .id(TO_ACCOUNT_ID)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(0))
                .userId(userId)
                .build();

        transfer = Transfer.builder()
                .id(TRANSFER_ID)
                .publicId(UUID.randomUUID())
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        transferAddedEvent = new TransferAddedEvent(
                userId,
                transfer.getPublicId(),
                FROM_ACCOUNT_ID,
                TO_ACCOUNT_ID,
                TRANSFER_ID
        );
    }

    // ---------- SUCCESS ----------

    @Test
    void processTransfer_WhenSufficientBalance_ShouldCompleteTransfer() {
        when(transferRepository.findByIdPessimistic(TRANSFER_ID))
                .thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));
        when(transferMapper.mapToReadDto(transfer))
                .thenReturn(toReadDto(transfer, TransferStatus.COMPLETED));

        transferProcessor.processTransfer(transferAddedEvent);

        assertThat(fromAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(900));
        assertThat(toAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getProcessedAt()).isNotNull();

        verify(transferFailureHandler, never()).handle(anyLong(), anyString());
    }

    // ---------- IDEMPOTENCY ----------

    @Test
    void processTransfer_WhenTransferAlreadyCompleted_ShouldSkipWithoutFailure() {
        transfer.setStatus(TransferStatus.COMPLETED);
        when(transferRepository.findByIdPessimistic(TRANSFER_ID))
                .thenReturn(Optional.of(transfer));

        transferProcessor.processTransfer(transferAddedEvent);

        verify(accountRepository, never()).findByPublicIdPessimistic(anyLong());
        verify(transferMapper, never()).mapToReadDto(any(Transfer.class));
        verify(transferFailureHandler, never()).handle(anyLong(), anyString());
    }

    @Test
    void processTransfer_WhenStatusIsFailed_ShouldHandleAsFailure() {
        transfer.setStatus(TransferStatus.FAILED);
        when(transferRepository.findByIdPessimistic(TRANSFER_ID))
                .thenReturn(Optional.of(transfer));

        transferProcessor.processTransfer(transferAddedEvent);

        verify(accountRepository, never()).findByPublicIdPessimistic(anyLong());
        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error"));
    }

    // ---------- FAILURES ----------

    @Test
    void processTransfer_WhenInsufficientBalance_ShouldFailTransfer() {
        fromAccount.setBalance(BigDecimal.valueOf(50));
        when(transferRepository.findByIdPessimistic(TRANSFER_ID))
                .thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));

        transferProcessor.processTransfer(transferAddedEvent);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Insufficient funds"));
    }

    @Test
    void processTransfer_WhenTransferNotFound_ShouldHandleAsFailure() {
        when(transferRepository.findByIdPessimistic(TRANSFER_ID))
                .thenReturn(Optional.empty());

        transferProcessor.processTransfer(transferAddedEvent);

        verify(accountRepository, never()).findByPublicIdPessimistic(anyLong());
        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error"));
    }

    @Test
    void processTransfer_WhenFirstAccountNotFound_ShouldHandleAsFailure() {
        when(transferRepository.findByIdPessimistic(TRANSFER_ID))
                .thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.empty());

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error"));
    }

    @Test
    void processTransfer_WhenSecondAccountNotFound_ShouldHandleAsFailure() {
        when(transferRepository.findByIdPessimistic(TRANSFER_ID))
                .thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.empty());

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error"));
    }

    @Test
    void processTransfer_WhenUnexpectedException_ShouldHandleAsFailure() {
        when(transferRepository.findByIdPessimistic(TRANSFER_ID))
                .thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenThrow(new RuntimeException("DB connection error"));

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error: DB connection error"));
        verify(transferMapper, never()).mapToReadDto(any(Transfer.class));
    }

    // ---------- DEADLOCK PREVENTION ----------

    @Test
    void processTransfer_WhenFromIdGreaterThanToId_ShouldLockInAscendingOrder() {
        long fromId = 5L;
        long toId = 2L;
        long transferId = 200L;

        Account lowId = Account.builder()
                .id(2L)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(0))
                .build();

        Account highId = Account.builder()
                .id(5L)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(500))
                .build();

        Transfer testTransfer = Transfer.builder()
                .id(transferId)
                .publicId(UUID.randomUUID())
                .fromAccount(highId)
                .toAccount(lowId)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        TransferAddedEvent event = new TransferAddedEvent(
                UUID.randomUUID(),
                testTransfer.getPublicId(),
                fromId,
                toId,
                transferId
        );

        when(transferRepository.findByIdPessimistic(transferId))
                .thenReturn(Optional.of(testTransfer));
        when(accountRepository.findByPublicIdPessimistic(2L))
                .thenReturn(Optional.of(lowId));
        when(accountRepository.findByPublicIdPessimistic(5L))
                .thenReturn(Optional.of(highId));
        when(transferMapper.mapToReadDto(testTransfer))
                .thenReturn(toReadDto(testTransfer, TransferStatus.COMPLETED));

        transferProcessor.processTransfer(event);

        var inOrder = inOrder(accountRepository);
        inOrder.verify(accountRepository).findByPublicIdPessimistic(2L);
        inOrder.verify(accountRepository).findByPublicIdPessimistic(5L);
    }

    @Test
    void processTransfer_WhenFromIdLessThanToId_ShouldLockInAscendingOrder() {
        long fromId = 3L;
        long toId = 7L;
        long transferId = 300L;

        Account lowId = Account.builder()
                .id(3L)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(500))
                .build();

        Account highId = Account.builder()
                .id(7L)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(0))
                .build();

        Transfer testTransfer = Transfer.builder()
                .id(transferId)
                .publicId(UUID.randomUUID())
                .fromAccount(lowId)
                .toAccount(highId)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        TransferAddedEvent event = new TransferAddedEvent(
                UUID.randomUUID(),
                testTransfer.getPublicId(),
                fromId,
                toId,
                transferId
        );

        when(transferRepository.findByIdPessimistic(transferId))
                .thenReturn(Optional.of(testTransfer));
        when(accountRepository.findByPublicIdPessimistic(3L))
                .thenReturn(Optional.of(lowId));
        when(accountRepository.findByPublicIdPessimistic(7L))
                .thenReturn(Optional.of(highId));
        when(transferMapper.mapToReadDto(testTransfer))
                .thenReturn(toReadDto(testTransfer, TransferStatus.COMPLETED));

        transferProcessor.processTransfer(event);

        var inOrder = inOrder(accountRepository);
        inOrder.verify(accountRepository).findByPublicIdPessimistic(3L);
        inOrder.verify(accountRepository).findByPublicIdPessimistic(7L);
    }

    // ---------- HELPER ----------

    private TransferReadDto toReadDto(Transfer t, TransferStatus status) {
        return new TransferReadDto(
                t.getPublicId(),
                t.getFromAccount() != null ? t.getFromAccount().getPublicId() : null,
                t.getToAccount() != null ? t.getToAccount().getPublicId() : null,
                t.getAmount(),
                status,
                t.getCreatedAt(),
                t.getProcessedAt(),
                t.getReason()
        );
    }
}