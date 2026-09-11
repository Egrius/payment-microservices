package by.egrius.payment_service.service;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.event.TransferAddedEvent;
import by.egrius.payment_service.event.TransferProcessedEvent;
import by.egrius.payment_service.exception.payment_service.*;
import by.egrius.payment_service.mapper.TransferMapper;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransferProcessor {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final CacheService cacheService;
    private final TransferMapper transferMapper;
    private final TransferFailureHandler transferFailureHandler;

    private final RabbitTemplate rabbitTemplate;

    private static final String TRANSFERS_CACHE_NAME = "transfers";
    private static final String EXCHANGE_NAME = "payment.exchange";
    private static final String ROUTING_KEY = "transfer.completed";


    @RabbitListener(queues = "processing.queue")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processTransfer(TransferAddedEvent addedEvent) {

        long fromAccountId = addedEvent.getFromAccountId();
        long toAccountId = addedEvent.getToAccountId();
        long transferId = addedEvent.getTransferId();

        try {
            log.debug("Processing transfer: fromAccountPublicId={}, toAccountPublicId={}, transferId={}",
                    fromAccountId, toAccountId, transferId);

            executeTransfer(addedEvent.getUserId(), addedEvent.getTransferPublicId(), transferId, fromAccountId, toAccountId);

        } catch (InsufficientFundsException e) {
            log.warn("Transfer {} failed due to insufficient funds", transferId);
            transferFailureHandler.handle(transferId, "Insufficient funds: " + e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error processing transfer {}", transferId, e);
            transferFailureHandler.handle(transferId, "Internal error: " + e.getMessage());
        }
    }

    private void executeTransfer(UUID userId, UUID transferPublicId, long transferId, long fromAccountId, long toAccountId) {

        String key = CacheService.generateKey(userId.toString(), transferPublicId.toString());

        TransferReadDto transferReadDto = cacheService.get(TRANSFERS_CACHE_NAME, key, TransferReadDto.class);

        if (transferReadDto == null) {

            transferReadDto = transferMapper.mapToReadDto(transferRepository.findById(transferId)
                    .orElseThrow(() -> new TransferNotFoundException(transferId)));

            cacheService.put(TRANSFERS_CACHE_NAME, key, transferReadDto);
        }

        // Check if already processed
        if (transferReadDto.status() != TransferStatus.PENDING) {
            throw new TransferAlreadyProcessedException(transferId, transferReadDto.status());
        }

        // Block in order of id to prevent deadlock
        long firstId = Math.min(fromAccountId, toAccountId);
        long secondId = Math.max(fromAccountId, toAccountId);

        Account first = accountRepository.findByPublicIdPessimistic(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));

        Account second = accountRepository.findByPublicIdPessimistic(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        // Identify 'from' and 'to' account
        Account fromAccount = (firstId == fromAccountId) ? first : second;
        Account toAccount = (secondId == toAccountId) ? second : first;

        // Check the balance
        if (fromAccount.getBalance().compareTo(transferReadDto.amount()) < 0) {
            throw new InsufficientFundsException(fromAccount.getBalance(), transferReadDto.amount());
        }

        // Balance update
        fromAccount.setBalance(fromAccount.getBalance().subtract(transferReadDto.amount()));
        toAccount.setBalance(toAccount.getBalance().add(transferReadDto.amount()));

        LocalDateTime processedAt = LocalDateTime.now();

        // Save all changes
        accountRepository.saveAll(List.of(fromAccount, toAccount));
        int updated = transferRepository.updateTransferStatus(transferReadDto.publicId(), TransferStatus.COMPLETED, LocalDateTime.now());

        if (updated > 0) {
            TransferReadDto updatedTransferReadDto = new TransferReadDto(
                    transferReadDto.publicId(),
                    transferReadDto.toAccountPublicId(),
                    transferReadDto.fromAccountPublicId(),
                    transferReadDto.amount(),
                    TransferStatus.COMPLETED,
                    transferReadDto.createdAt(),
                    processedAt,
                    transferReadDto.reason()
            );

            cacheService.put(TRANSFERS_CACHE_NAME, key, updatedTransferReadDto);

            // Sending notification after commit
            TransactionUtils.afterCommit(() -> sendProcessedNotification(userId, updatedTransferReadDto));

            log.info("Transfer {} processed successfully", transferId);
        } else {
            throw new TransferUpdateException(transferReadDto.publicId());
        }
    }

    // TODO outbox pattern, but may be excessive for such a project
    private void sendProcessedNotification(UUID userId, TransferReadDto transferReadDto) {
        try {
            log.info("Sending TransferProcessedEvent to exchange={}, routingKey={}, transferId={}, status={}",
                    EXCHANGE_NAME, ROUTING_KEY, transferReadDto.publicId(), transferReadDto.status());
            rabbitTemplate.convertAndSend(
                    EXCHANGE_NAME,
                    ROUTING_KEY,
                    new TransferProcessedEvent(userId, transferReadDto)
            );
            log.info("TransferProcessedEvent sent: transferId={}", transferReadDto.publicId());

        } catch (Exception e) {
            log.error("Failed to send notification of TransferProcessedEvent for transfer {}",
                    transferReadDto.publicId(), e);
        }
    }
}