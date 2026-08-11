package by.egrius.api_gateway.unit.service;

import by.egrius.api_gateway.dto.transfer.TransferCreateDto;
import by.egrius.api_gateway.dto.transfer.TransferReadDto;
import by.egrius.api_gateway.entity.Account;
import by.egrius.api_gateway.entity.Transfer;
import by.egrius.api_gateway.entity.TransferStatus;
import by.egrius.api_gateway.event.TransferAddedEvent;
import by.egrius.api_gateway.event.publisher.TransferAddedEventPublisher;
import by.egrius.api_gateway.mapper.TransferMapper;
import by.egrius.api_gateway.repository.AccountRepository;
import by.egrius.api_gateway.repository.TransferRepository;
import by.egrius.api_gateway.service.TransferService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceUnitTests {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferMapper transferMapper;

    @Mock
    private TransferAddedEventPublisher eventPublisher;

    @InjectMocks
    private TransferService transferService;

    private UUID userPublicId;
    private UUID fromAccountPublicId;
    private UUID toAccountPublicId;
    private Account fromAccount;
    private Account toAccount;
    private Transfer transfer;
    private Transfer savedTransfer;
    private TransferReadDto transferReadDto;
    private TransferCreateDto createDto;

    @BeforeEach
    void setUp() {
        userPublicId = UUID.randomUUID();
        fromAccountPublicId = UUID.randomUUID();
        toAccountPublicId = UUID.randomUUID();

        fromAccount = Account.builder()
                .id(1L)
                .publicId(fromAccountPublicId)
                .userId(userPublicId)
                .balance(BigDecimal.valueOf(1000))
                .build();

        toAccount = Account.builder()
                .id(2L)
                .publicId(toAccountPublicId)
                .userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO)
                .build();

        transfer = Transfer.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.PENDING)
                .build();

        savedTransfer = Transfer.builder()
                .id(1L)
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.PENDING)
                .build();

        transferReadDto = new TransferReadDto(
                UUID.randomUUID(),
                fromAccountPublicId,
                toAccountPublicId,
                BigDecimal.valueOf(100),
                TransferStatus.PENDING,
                null,
                null,
                null
        );

        createDto = new TransferCreateDto(
                fromAccountPublicId,
                toAccountPublicId,
                BigDecimal.valueOf(100)
        );
    }

    @Test
    void createTransfer_WhenValid_ShouldCreateTransferAndPublishEvent() {
        
        when(accountRepository.findByPublicIdAndUserId(fromAccountPublicId, userPublicId))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicId(toAccountPublicId))
                .thenReturn(Optional.of(toAccount));
        when(transferRepository.save(any(Transfer.class))).thenReturn(savedTransfer);
        when(transferMapper.mapToReadDto(savedTransfer)).thenReturn(transferReadDto);
        doNothing().when(eventPublisher).publishEvent(any(TransferAddedEvent.class));

        
        TransferReadDto result = transferService.createTransfer(createDto, userPublicId);

       
        assertThat(result).isNotNull();
        assertThat(result.publicId()).isEqualTo(transferReadDto.publicId());

        verify(accountRepository, times(1))
                .findByPublicIdAndUserId(fromAccountPublicId, userPublicId);
        verify(accountRepository, times(1))
                .findByPublicId(toAccountPublicId);
        verify(transferRepository, times(1)).save(any(Transfer.class));

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        TransferAddedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getTransferId()).isEqualTo(savedTransfer.getId());
    }

    @Test
    void createTransfer_WhenFromAccountDoesNotExist_ShouldThrowException() {
        
        when(accountRepository.findByPublicIdAndUserId(fromAccountPublicId, userPublicId))
                .thenReturn(Optional.empty());

        
        assertThatThrownBy(() -> transferService.createTransfer(createDto, userPublicId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("'from account' does not exist");

        verify(accountRepository, never()).findByPublicId(toAccountPublicId);
        verify(transferRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createTransfer_WhenToAccountDoesNotExist_ShouldThrowException() {
        
        when(accountRepository.findByPublicIdAndUserId(fromAccountPublicId, userPublicId))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByPublicId(toAccountPublicId))
                .thenReturn(Optional.empty());

        
        assertThatThrownBy(() -> transferService.createTransfer(createDto, userPublicId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("'to account' does not exist");

        verify(transferRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createTransfer_WhenFromAndToAccountsAreSame_ShouldThrowIllegalArgumentException() {
        
        TransferCreateDto invalidDto = new TransferCreateDto(
                fromAccountPublicId,
                fromAccountPublicId,
                BigDecimal.valueOf(100)
        );

        
        assertThatThrownBy(() -> transferService.createTransfer(invalidDto, userPublicId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Can't create transfer for the same account");

        verify(accountRepository, never()).findByPublicIdAndUserId(any(), any());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void getTransferStatus_WhenTransferExists_ShouldReturnTransfer() {
        
        UUID transferPublicId = UUID.randomUUID();
        Transfer transferWithPublicId = Transfer.builder()
                .id(1L)
                .publicId(transferPublicId)
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.COMPLETED)
                .build();

        when(transferRepository.findByTransferPublicId_and_UserPublicId(transferPublicId, userPublicId))
                .thenReturn(Optional.of(transferWithPublicId));
        when(transferMapper.mapToReadDto(transferWithPublicId)).thenReturn(transferReadDto);

        
        TransferReadDto result = transferService.getTransferStatus(transferPublicId, userPublicId);

       
        assertThat(result).isNotNull();
        verify(transferRepository, times(1))
                .findByTransferPublicId_and_UserPublicId(transferPublicId, userPublicId);
    }

    @Test
    void getTransferStatus_WhenTransferNotFound_ShouldThrowException() {
        
        UUID transferPublicId = UUID.randomUUID();

        when(transferRepository.findByTransferPublicId_and_UserPublicId(transferPublicId, userPublicId))
                .thenReturn(Optional.empty());

        
        assertThatThrownBy(() -> transferService.getTransferStatus(transferPublicId, userPublicId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(transferPublicId.toString());

        verify(transferMapper, never()).mapToReadDto(any());
    }
}