package by.egrius.payment_service.service;

import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.event.TransferProcessedEvent;
import by.egrius.payment_service.event.publisher.TransferProcessedEventPublisher;
import by.egrius.payment_service.exception.payment_service.*;
import by.egrius.payment_service.mapper.TransferMapper;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
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
    private final TransferProcessedEventPublisher transferProcessedEventPublisher;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TransferMapper transferMapper;

    @Async("transfer-task-pool")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processTransfer(long fromAccountId, long toAccountId, Long transferId) {
        try {
            log.debug("Processing transfer: fromAccountId={}, toAccountId={}, transferId={}",
                    fromAccountId, toAccountId, transferId);

            executeTransfer(fromAccountId, toAccountId, transferId);

        } catch (InsufficientFundsException e) {
            log.warn("Transfer {} failed due to insufficient funds", transferId);
            handleTransferFailure(transferId, fromAccountId, toAccountId,
                    "Insufficient funds: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing transfer {}", transferId, e);
            handleTransferFailure(transferId, fromAccountId, toAccountId,
                    "Internal error: " + e.getMessage());
            // Re-throw to trigger retry for recoverable errors
            if (e instanceof OptimisticLockingFailureException ||
                    e instanceof org.springframework.dao.DataIntegrityViolationException) {
                throw e;
            }
        }
    }

    private void executeTransfer(long fromAccountId, long toAccountId, Long transferId) {
        // Find the transfer
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        // Check if already processed
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new TransferAlreadyProcessedException(transferId, transfer.getStatus());
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

        // Transfer status update
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setProcessedAt(LocalDateTime.now());

        // Save all changes
        accountRepository.saveAll(List.of(fromAccount, toAccount));
        Transfer savedTransfer = transferRepository.save(transfer);

        // Update cache
        updateCache(fromAccount.getUserId(), savedTransfer);

        // Publish completion event
        publishProcessedEvent(fromAccountId, toAccountId, transfer, TransferStatus.COMPLETED);

        log.info("Transfer {} processed successfully", transferId);
    }

    private void updateCache(UUID userId, Transfer transfer) {
        try {
            Cache cache = cacheManager.getCache("transfers");
            if (cache != null) {
                String key = userId + "_" + transfer.getPublicId();
                cache.put(key, transferMapper.mapToReadDto(transfer));
                log.debug("Updated cache for transfer: key={}", key);
            }
        } catch (Exception e) {
            // Don't throw - cache failure shouldn't break the transfer
            log.warn("Failed to update cache for transfer {}", transfer.getId(), e);
        }
    }

    // I better still throw an exception to keep consistency
    private void publishProcessedEvent(long fromAccountId, long toAccountId,
                                       Transfer transfer, TransferStatus status) {
        try {
            transferProcessedEventPublisher.publishEvent(
                    new TransferProcessedEvent(
                            this,
                            fromAccountId,
                            toAccountId,
                            transfer.getId(),
                            transfer.getAmount(),
                            status
                    )
            );
        } catch (Exception e) {
            log.error("Failed to publish TransferProcessedEvent for transfer {}",
                    transfer.getId(), e);
            // Don't throw - event publishing failure shouldn't break the transfer
        }
    }

    private void handleTransferFailure(Long transferId, long fromAccountId, long toAccountId, String reason) {
        try {
            Transfer transfer = transferRepository.findById(transferId).orElse(null);
            if (transfer != null && transfer.getStatus() == TransferStatus.PENDING) {
                transfer.setStatus(TransferStatus.FAILED);
                transfer.setReason(reason);
                transfer.setProcessedAt(LocalDateTime.now());
                transferRepository.save(transfer);

                // Update cache if transfer was cached
                try {
                    Cache cache = cacheManager.getCache("transfers");
                    if (cache != null && transfer.getFromAccount() != null) {
                        UUID userId = transfer.getFromAccount().getUserId();
                        String key = userId + "_" + transfer.getPublicId();
                        cache.evict(key);
                        log.debug("Evicted failed transfer from cache: key={}", key);
                    }
                } catch (Exception cacheEx) {
                    log.warn("Failed to evict transfer {} from cache", transferId, cacheEx);
                }

                publishProcessedEvent(fromAccountId, toAccountId, transfer, TransferStatus.FAILED);
                log.info("Transfer {} marked as FAILED: {}", transferId, reason);
            }
        } catch (Exception saveEx) {
            log.error("Failed to update transfer {} status to FAILED", transferId, saveEx);
            throw new TransferProcessingException(transferId, "Failed to update transfer status", saveEx);
        }
    }
}
