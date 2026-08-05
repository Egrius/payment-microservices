package by.egrius.api_gateway.service;

import by.egrius.api_gateway.entity.Account;
import by.egrius.api_gateway.entity.Transfer;
import by.egrius.api_gateway.entity.TransferStatus;
import by.egrius.api_gateway.event.TransferProcessedEvent;
import by.egrius.api_gateway.event.publisher.TransferProcessedEventPublisher;
import by.egrius.api_gateway.repository.AccountRepository;
import by.egrius.api_gateway.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransferProcessor {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransferProcessedEventPublisher transferProcessedEventPublisher;

    @Async("transfer-task-pool")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processTransfer(long fromAccountId, long toAccountId, Long transferId) {
        try {
            log.debug("processTransfer() called, params got: " +
                            "fromAccountId: {} , toAccountId: {} , transferId: {}",
                    fromAccountId, toAccountId, transferId);

            // Find the transfer
            Transfer transfer = transferRepository.findById(transferId)
                    .orElseThrow(() -> new RuntimeException("Transfer not found"));

            // Block in order of id to prevent from deadlock
            long firstId = Math.min(fromAccountId, toAccountId);
            long secondId = Math.max(fromAccountId, toAccountId);

            Account first = accountRepository.findByPublicIdPessimistic(firstId)
                    .orElseThrow(() -> new RuntimeException("First account not found"));

            Account second = accountRepository.findByPublicIdPessimistic(secondId)
                    .orElseThrow(() -> new RuntimeException("Second account not found"));

            // Identify 'from' and 'to' account
            Account fromAccount = (firstId == fromAccountId) ? first : second;
            Account toAccount = (secondId == toAccountId) ? second : first;

            // Check the balance
            if (fromAccount.getBalance().compareTo(transfer.getAmount()) < 0) {
                transfer.setStatus(TransferStatus.FAILED);
                transfer.setReason("Insufficient funds");
                transferRepository.save(transfer);

                transferProcessedEventPublisher.publishEvent(
                        new TransferProcessedEvent(
                                this, fromAccountId, toAccountId,
                                transfer.getId(), transfer.getAmount(), transfer.getStatus()
                        )
                );

                return;
            }

            // Balance update
            fromAccount.setBalance(fromAccount.getBalance().subtract(transfer.getAmount()));
            toAccount.setBalance(toAccount.getBalance().add(transfer.getAmount()));

            // Transfer status update
            transfer.setStatus(TransferStatus.COMPLETED);
            transfer.setProcessedAt(LocalDateTime.now());

            accountRepository.saveAll(List.of(fromAccount, toAccount));
            transferRepository.save(transfer);

            transferProcessedEventPublisher.publishEvent(
                    new TransferProcessedEvent(
                            this, fromAccountId, toAccountId,
                            transfer.getId(), transfer.getAmount(), transfer.getStatus()
                    )
            );

            log.debug("Processed transfer: {}", transfer);

        } catch (Exception e) {
            // Logging an error without throwing it to keep thread alive
            log.error("Failed to process transfer {}", transferId, e);

            try {
                Transfer transfer = transferRepository.findById(transferId).orElse(null);
                if (transfer != null && transfer.getStatus() == TransferStatus.PENDING) {
                    transfer.setStatus(TransferStatus.FAILED);
                    transfer.setReason("Internal error: " + e.getMessage());
                    transferRepository.save(transfer);

                    transferProcessedEventPublisher.publishEvent(
                            new TransferProcessedEvent(
                                    this, fromAccountId, toAccountId,
                                    transfer.getId(), transfer.getAmount(), transfer.getStatus()
                            )
                    );
                }
            } catch (Exception saveEx) {
                log.error("Failed to update transfer status to FAILED", saveEx);
            }
        }
    }


}
