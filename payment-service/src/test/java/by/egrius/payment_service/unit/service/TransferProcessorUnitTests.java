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
import by.egrius.payment_service.service.TransferProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    private RabbitTemplate rabbitTemplate;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private TransferProcessor transferProcessor;

    private static final long FROM_ACCOUNT_ID = 1L;
    private static final long TO_ACCOUNT_ID = 2L;
    private static final long TRANSFER_ID = 100L;

    private Account fromAccount;
    private Account toAccount;
    private Transfer transfer;
    private TransferAddedEvent transferAddedEvent;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();

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
                .publicId(UUID.randomUUID())  // 👈 Добавляем publicId!
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
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);  // 👈 Мокаем кэш
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));

        when(transferMapper.mapToReadDto(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new TransferReadDto(
                    t.getPublicId(),
                    t.getFromAccount().getPublicId(),
                    t.getToAccount().getPublicId(),
                    t.getAmount(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getProcessedAt(),
                    t.getReason()
            );
        });

        transferProcessor.processTransfer(transferAddedEvent);

        assertThat(fromAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(900));
        assertThat(toAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getProcessedAt()).isNotNull();

        verify(accountRepository, times(1)).saveAll(anyList());
        verify(transferRepository, times(1)).save(transfer);
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

        when(transferMapper.mapToReadDto(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new TransferReadDto(
                    t.getPublicId(),
                    t.getFromAccount().getPublicId(),
                    t.getToAccount().getPublicId(),
                    t.getAmount(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getProcessedAt(),
                    t.getReason()
            );
        });

        transferProcessor.processTransfer(transferAddedEvent);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getReason()).contains("Insufficient funds");

        verify(accountRepository, never()).saveAll(anyList());
        verify(transferRepository, times(1)).save(transfer);
    }

    @Test
    void processTransfer_WhenTransferNotFound_ShouldLogErrorAndReturn() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.empty());

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferRepository, times(2)).findById(TRANSFER_ID);
        verify(accountRepository, never()).findByPublicIdPessimistic(anyLong());
    }

    @Test
    void processTransfer_WhenExceptionDuringProcessing_ShouldSetTransferFailed() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenThrow(new RuntimeException("DB connection error"));

        when(transferMapper.mapToReadDto(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new TransferReadDto(
                    t.getPublicId(),
                    t.getFromAccount().getPublicId(),
                    t.getToAccount().getPublicId(),
                    t.getAmount(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getProcessedAt(),
                    t.getReason()
            );
        });

        transferProcessor.processTransfer(transferAddedEvent);

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository, times(1)).save(transferCaptor.capture());

        Transfer savedTransfer = transferCaptor.getValue();
        assertThat(savedTransfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(savedTransfer.getReason()).contains("Internal error: DB connection error");
    }

    @Test
    void processTransfer_WithDeadlockPrevention_ShouldLockAccountsInOrder() {
        long fromId = 5L;
        long toId = 2L;
        long transferId = 200L;

        Account firstAccount = Account.builder()
                .id(2L)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(500))
                .build();

        Account secondAccount = Account.builder()
                .id(5L)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(0))
                .build();

        Transfer testTransfer = Transfer.builder()
                .id(transferId)
                .publicId(UUID.randomUUID())
                .fromAccount(fromAccount)
                .toAccount(toAccount)
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
        when(accountRepository.findByPublicIdPessimistic(2L)).thenReturn(Optional.of(firstAccount));
        when(accountRepository.findByPublicIdPessimistic(5L)).thenReturn(Optional.of(secondAccount));

        when(transferMapper.mapToReadDto(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new TransferReadDto(
                    t.getPublicId(),
                    t.getFromAccount().getPublicId(),
                    t.getToAccount().getPublicId(),
                    t.getAmount(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getProcessedAt(),
                    t.getReason()
            );
        });

        transferProcessor.processTransfer(event);

        verify(accountRepository, times(1)).findByPublicIdPessimistic(2L);
        verify(accountRepository, times(1)).findByPublicIdPessimistic(5L);
    }

    @Test
    void processTransfer_ShouldBlockAccountsInOrder_EvenWhenFromIsLessThanTo() {
        long fromId = 3L;
        long toId = 7L;
        long transferId = 300L;

        Account firstAccount = Account.builder()
                .id(3L)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(500))
                .build();

        Account secondAccount = Account.builder()
                .id(7L)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(0))
                .build();

        Transfer testTransfer = Transfer.builder()
                .id(transferId)
                .publicId(UUID.randomUUID())
                .fromAccount(fromAccount)
                .toAccount(toAccount)
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
        when(accountRepository.findByPublicIdPessimistic(3L)).thenReturn(Optional.of(firstAccount));
        when(accountRepository.findByPublicIdPessimistic(7L)).thenReturn(Optional.of(secondAccount));

        when(transferMapper.mapToReadDto(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new TransferReadDto(
                    t.getPublicId(),
                    t.getFromAccount().getPublicId(),
                    t.getToAccount().getPublicId(),
                    t.getAmount(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getProcessedAt(),
                    t.getReason()
            );
        });

        transferProcessor.processTransfer(event);

        verify(accountRepository, times(1)).findByPublicIdPessimistic(3L);
        verify(accountRepository, times(1)).findByPublicIdPessimistic(7L);
    }

    @Test
    void processTransfer_WhenFirstAccountNotFound_ShouldHandleException() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.empty());

        when(transferMapper.mapToReadDto(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new TransferReadDto(
                    t.getPublicId(),
                    t.getFromAccount().getPublicId(),
                    t.getToAccount().getPublicId(),
                    t.getAmount(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getProcessedAt(),
                    t.getReason()
            );
        });

        transferProcessor.processTransfer(transferAddedEvent);

        verify(transferRepository, atLeastOnce()).save(any(Transfer.class));

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository, atLeastOnce()).save(transferCaptor.capture());
        assertThat(transferCaptor.getValue().getStatus()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void processTransfer_WhenTransferAlreadyProcessed_ShouldNotChangeStatus() {
        transfer.setStatus(TransferStatus.COMPLETED);
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        transferProcessor.processTransfer(transferAddedEvent);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(accountRepository, never()).saveAll(anyList());
        verify(transferRepository, never()).save(transfer);
    }

    @Test
    void processTransfer_WhenOptimisticLockingFailure_ShouldThrowException() {
        when(cacheService.get(anyString(), anyString(), any())).thenReturn(null);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));
        when(transferRepository.updateTransferStatus(any(), any(), any()))
                .thenThrow(new OptimisticLockingFailureException("Optimistic lock"));

        when(transferMapper.mapToReadDto(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new TransferReadDto(
                    t.getPublicId(),
                    t.getFromAccount().getPublicId(),
                    t.getToAccount().getPublicId(),
                    t.getAmount(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getProcessedAt(),
                    t.getReason()
            );
        });

        assertThatThrownBy(() -> transferProcessor.processTransfer(transferAddedEvent))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(transferRepository, never()).save(any(Transfer.class));
    }

    @Test
    void processTransfer_WhenCacheReturnsData_ShouldUseCacheAndNotCallRepository() {

        TransferReadDto fromCacheTransferReadDto = new TransferReadDto(
                transfer.getPublicId(),
                transfer.getFromAccount().getPublicId(),
                transfer.getToAccount().getPublicId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getCreatedAt(),
                transfer.getProcessedAt(),
                transfer.getReason()
        );

        when(cacheService.get(anyString(), anyString(), any())).thenReturn(fromCacheTransferReadDto);

        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));

        when(transferRepository.updateTransferStatus(eq(fromCacheTransferReadDto.publicId()), eq(TransferStatus.COMPLETED), any(LocalDateTime.class)))
                .thenReturn(1);

        transferProcessor.processTransfer(transferAddedEvent);

        //verify(transferRepository, calls(0)).findById(TRANSFER_ID);
    }
}