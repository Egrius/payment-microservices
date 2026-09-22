package by.egrius.payment_service.service;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
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

        Transfer transfer = transferRepository.findByIdPessimistic(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            log.warn("Transfer {} already completed, skipping", transferId);
            return;
        }

        // Check if already processed
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new TransferNotPendingException(transferId, transfer.getStatus());
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
        if (fromAccount.getBalance().compareTo(transfer.getAmount()) < 0) {
            throw new InsufficientFundsException(fromAccount.getBalance(), transfer.getAmount());
        }

        // Balance update
        fromAccount.setBalance(fromAccount.getBalance().subtract(transfer.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(transfer.getAmount()));

        LocalDateTime processedAt = LocalDateTime.now();

        // Save all changes
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setProcessedAt(processedAt);

        TransferReadDto updatedTransferReadDto = transferMapper.mapToReadDto(transfer);

            // Sending notification after commit
            TransactionUtils.afterCommit(() -> {

                cacheService.put(TRANSFERS_CACHE_NAME, key, updatedTransferReadDto);
                sendProcessedNotification(userId, updatedTransferReadDto);


                log.info("Transfer {} processed successfully", transferId);
            });
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