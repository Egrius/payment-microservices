package by.egrius.api_gateway.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransferMapper transferMapper;

    private final TransferAddedEventPublisher transferAddedEventPublisher;

    /**
     * Creates a transfer
     * - Saves a transfer in the DB with PENDING status
     * - Creates an event for the created transfer to process the transfer independently
     * - Returns dto contains transfer's info (pending one)
     */
    @Transactional
    public TransferReadDto createTransfer(TransferCreateDto createDto) {

        // Find accounts for the transfer
        Account fromAccount = accountRepository.findByPublicId(createDto.fromAccountId()).orElseThrow(
                () -> new RuntimeException("'from account' does not exist")
        );
        Account toAccount = accountRepository.findByPublicId(createDto.toAccountId()).orElseThrow(
                () -> new RuntimeException("'to account' does not exist")
        );

        // Creating PENDING transfer not to hold the http thread and process the transfer independently
        Transfer transfer = Transfer.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(createDto.amount())
                .status(TransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Transfer savedTransfer = transferRepository.save(transfer);

        // Flush to not wait until the commit, to let the listener find it right after the event has been published
        // But I also could add transactional event listener, that would be safer
        transferRepository.flush();

        // Publish an event to process the transfer in TransferAddedEventListener
        transferAddedEventPublisher.publishEvent(
                new TransferAddedEvent(this, fromAccount.getId(), toAccount.getId(), savedTransfer.getId())
        );

        return transferMapper.mapToReadDto(savedTransfer);
    }

    public TransferReadDto getTransferStatus(Long id) {
        Transfer transfer = transferRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Couldn't find a transfer with id %d".formatted(id)));

        return transferMapper.mapToReadDto(transfer);
    }
}