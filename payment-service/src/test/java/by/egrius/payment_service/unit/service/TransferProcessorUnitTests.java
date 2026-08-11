package by.egrius.payment_service.unit.service;

import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.service.TransferProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferProcessorUnitTests {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransferProcessor transferProcessor;

    private static final long FROM_ACCOUNT_ID = 1L;
    private static final long TO_ACCOUNT_ID = 2L;
    private static final long TRANSFER_ID = 100L;

    private Account fromAccount;
    private Account toAccount;
    private Transfer transfer;

    @BeforeEach
    void setUp() {
        fromAccount = Account.builder()
                .id(FROM_ACCOUNT_ID)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(1000))
                .build();

        toAccount = Account.builder()
                .id(TO_ACCOUNT_ID)
                .publicId(UUID.randomUUID())
                .balance(BigDecimal.valueOf(0))
                .build();

        transfer = Transfer.builder()
                .id(TRANSFER_ID)
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.PENDING)
                .build();
    }

    @Test
    void processTransfer_WhenSufficientBalance_ShouldCompleteTransfer() {

        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));


        transferProcessor.processTransfer(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, TRANSFER_ID);


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
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicIdPessimistic(TO_ACCOUNT_ID))
                .thenReturn(Optional.of(toAccount));


        transferProcessor.processTransfer(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, TRANSFER_ID);


        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getReason()).isEqualTo("Insufficient funds");

        verify(accountRepository, never()).saveAll(anyList());
        verify(transferRepository, times(1)).save(transfer);
    }

    @Test
    void processTransfer_WhenTransferNotFound_ShouldLogErrorAndSetFailed() {

        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.empty());


        transferProcessor.processTransfer(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, TRANSFER_ID);


        verify(transferRepository, times(2)).findById(TRANSFER_ID);
        verify(accountRepository, never()).findByPublicIdPessimistic(anyLong());
    }

    @Test
    void processTransfer_WhenExceptionDuringProcessing_ShouldSetTransferFailed() {

        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenThrow(new RuntimeException("DB connection error"));


        transferProcessor.processTransfer(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, TRANSFER_ID);


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
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.PENDING)
                .build();

        when(transferRepository.findById(transferId)).thenReturn(Optional.of(testTransfer));
        when(accountRepository.findByPublicIdPessimistic(2L)).thenReturn(Optional.of(firstAccount));
        when(accountRepository.findByPublicIdPessimistic(5L)).thenReturn(Optional.of(secondAccount));


        transferProcessor.processTransfer(fromId, toId, transferId);


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
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.PENDING)
                .build();

        when(transferRepository.findById(transferId)).thenReturn(Optional.of(testTransfer));
        when(accountRepository.findByPublicIdPessimistic(3L)).thenReturn(Optional.of(firstAccount));
        when(accountRepository.findByPublicIdPessimistic(7L)).thenReturn(Optional.of(secondAccount));


        transferProcessor.processTransfer(fromId, toId, transferId);


        verify(accountRepository, times(1)).findByPublicIdPessimistic(3L);
        verify(accountRepository, times(1)).findByPublicIdPessimistic(7L);
    }

    @Test
    void processTransfer_WhenFirstAccountNotFound_ShouldHandleException() {

        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountRepository.findByPublicIdPessimistic(FROM_ACCOUNT_ID))
                .thenReturn(Optional.empty());


        transferProcessor.processTransfer(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, TRANSFER_ID);


        verify(transferRepository, atLeastOnce()).save(any(Transfer.class));

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository, atLeastOnce()).save(transferCaptor.capture());
        assertThat(transferCaptor.getValue().getStatus()).isEqualTo(TransferStatus.FAILED);
    }
}