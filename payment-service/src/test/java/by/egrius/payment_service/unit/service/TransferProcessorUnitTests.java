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
import org.springframework.dao.OptimisticLockingFailureException;

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
                .build();

        transferAddedEvent = new TransferAddedEvent(
                userId,
                transfer.getPublicId(),
                FROM_ACCOUNT_ID,
                TO_ACCOUNT_ID,
                TRANSFER_ID
        );
    }

    @Test
    void processTransfer_WhenSufficientBalance_ShouldCompleteTransfer() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));
        when(transferMapper.mapToReadDto(transfer)).thenReturn(toReadDto(transfer, TransferStatus.PENDING));
        when(transferRepository.updateTransferStatus(eq(transfer.getPublicId()), eq(TransferStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(1);

        transferProcessor.processTransfer(transferAddedEvent);

        assertThat(fromAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(900));
        assertThat(toAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));

        verify(accountRepository, times(1)).saveAll(anyList());
        verify(transferRepository, times(1))
                .updateTransferStatus(eq(transfer.getPublicId()), eq(TransferStatus.COMPLETED), any(LocalDateTime.class));
        verify(transferFailureHandler, never()).handle(anyLong(), anyString());
    }

    @Test
    void processTransfer_WhenCacheReturnsData_ShouldUseCacheAndNotCallRepository() {
        TransferReadDto cached = toReadDto(transfer, TransferStatus.PENDING);

        when(cacheService.get(anyString(), anyString(), any())).thenReturn(cached);
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));
        when(transferRepository.updateTransferStatus(eq(transfer.getPublicId()), eq(TransferStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(1);

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferRepository, never()).findById(anyLong());
        verify(transferMapper, never()).mapToReadDto(any(Transfer.class));
        verify(cacheService, times(1)).get(anyString(), anyString(), any());
    }


    @Test
    void processTransfer_WhenInsufficientBalance_ShouldFailTransfer() {
        fromAccount.setBalance(BigDecimal.valueOf(50));
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));
        when(transferMapper.mapToReadDto(transfer)).thenReturn(toReadDto(transfer, TransferStatus.PENDING));

        transferProcessor.processTransfer(transferAddedEvent);

        verify(accountRepository, never()).saveAll(anyList());
        verify(transferRepository, never()).updateTransferStatus(any(), any(), any());
        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Insufficient funds"));
    }


    @Test
    void processTransfer_WhenTransferNotFound_ShouldHandleAsFailure() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.empty());

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferRepository, times(1)).findById(TRANSFER_ID);
        verify(accountRepository, never()).findByPublicIdPessimistic(anyLong());
        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error"));
    }


    @Test
    void processTransfer_WhenTransferAlreadyProcessed_ShouldNotChangeStatus() {
        transfer.setStatus(TransferStatus.COMPLETED);
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(transferMapper.mapToReadDto(transfer)).thenReturn(toReadDto(transfer, TransferStatus.COMPLETED));

        transferProcessor.processTransfer(transferAddedEvent);

        verify(accountRepository, never()).saveAll(anyList());
        verify(transferRepository, never()).updateTransferStatus(any(), any(), any());
        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("already processed"));
    }


    @Test
    void processTransfer_WhenExceptionDuringProcessing_ShouldHandleAsFailure() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(transferMapper.mapToReadDto(transfer)).thenReturn(toReadDto(transfer, TransferStatus.PENDING));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenThrow(new RuntimeException("DB connection error"));

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error: DB connection error"));
        verify(transferRepository, never()).save(any(Transfer.class));
    }

    @Test
    void processTransfer_WhenFirstAccountNotFound_ShouldHandleAsFailure() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(transferMapper.mapToReadDto(transfer)).thenReturn(toReadDto(transfer, TransferStatus.PENDING));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.empty());

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error"));
    }


    @Test
    void processTransfer_WhenOptimisticLockingFailure_ShouldHandleAsFailure() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(transferMapper.mapToReadDto(transfer)).thenReturn(toReadDto(transfer, TransferStatus.PENDING));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));
        when(transferRepository.updateTransferStatus(any(), any(), any()))
                .thenThrow(new OptimisticLockingFailureException("Optimistic lock"));

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferFailureHandler, times(1))
                .handle(eq(TRANSFER_ID), contains("Internal error"));
    }

    @Test
    void processTransfer_WithDeadlockPrevention_ShouldLockAccountsInOrder() {
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
                .build();

        TransferAddedEvent event = new TransferAddedEvent(
                UUID.randomUUID(),
                testTransfer.getPublicId(),
                fromId,
                toId,
                transferId
        );

        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(testTransfer));
        when(transferMapper.mapToReadDto(testTransfer)).thenReturn(toReadDto(testTransfer, TransferStatus.PENDING));
        when(accountRepository.findByPublicIdPessimistic(2L)).thenReturn(Optional.of(lowId));
        when(accountRepository.findByPublicIdPessimistic(5L)).thenReturn(Optional.of(highId));
        when(transferRepository.updateTransferStatus(eq(testTransfer.getPublicId()), eq(TransferStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(1);

        transferProcessor.processTransfer(event);

        verify(accountRepository, times(1)).findByPublicIdPessimistic(2L);
        verify(accountRepository, times(1)).findByPublicIdPessimistic(5L);
    }

    @Test
    void processTransfer_ShouldBlockAccountsInOrder_EvenWhenFromIsLessThanTo() {
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
                .build();

        TransferAddedEvent event = new TransferAddedEvent(
                UUID.randomUUID(),
                testTransfer.getPublicId(),
                fromId,
                toId,
                transferId
        );

        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(testTransfer));
        when(transferMapper.mapToReadDto(testTransfer)).thenReturn(toReadDto(testTransfer, TransferStatus.PENDING));
        when(accountRepository.findByPublicIdPessimistic(3L)).thenReturn(Optional.of(lowId));
        when(accountRepository.findByPublicIdPessimistic(7L)).thenReturn(Optional.of(highId));
        when(transferRepository.updateTransferStatus(eq(testTransfer.getPublicId()), eq(TransferStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(1);

        transferProcessor.processTransfer(event);

        verify(accountRepository, times(1)).findByPublicIdPessimistic(3L);
        verify(accountRepository, times(1)).findByPublicIdPessimistic(7L);
    }

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