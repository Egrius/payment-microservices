package by.egrius.api_gateway.service;

/*
    Represents a task for asynchronous transfer processing.
    Requires a thread pool to work in
 */

import by.egrius.api_gateway.entity.Account;
import by.egrius.api_gateway.entity.Transfer;
import by.egrius.api_gateway.entity.TransferStatus;
import by.egrius.api_gateway.repository.AccountRepository;
import by.egrius.api_gateway.repository.TransferRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Component
@Scope("prototype")
@Getter
@Slf4j
public class TransferTask {

    private final long fromAccountId;
    private final long toAccountId;
    private final Long transferId;

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;

    public TransferTask(long fromAccountId, long toAccountId, Long transferId,
                        TransferRepository transferRepository,
                        AccountRepository accountRepository,
                        AccountService accountService) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.transferId = transferId;
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
    }


    @Transactional
    public void processTransfer() {
        try {
            // Find the transfer
            Transfer transfer = transferRepository.findById(transferId)
                    .orElseThrow(() -> new RuntimeException("Transfer not found"));

            // Block in order of id to prevent from deadlock
            long firstId = Math.min(fromAccountId, toAccountId);
            long secondId = Math.max(fromAccountId, toAccountId);

            Account first = accountRepository.findByIdPessimistic(firstId)
                    .orElseThrow(() -> new RuntimeException("First account not found"));
            Account second = accountRepository.findByIdPessimistic(secondId)
                    .orElseThrow(() -> new RuntimeException("Second account not found"));

            // Identify 'from' and 'to' account
            Account fromAccount = (firstId == fromAccountId) ? first : second;
            Account toAccount = (secondId == toAccountId) ? second : first;

            // Check the balance
            if (fromAccount.getBalance().compareTo(transfer.getAmount()) < 0) {
                transfer.setStatus(TransferStatus.FAILED);
                transfer.setReason("Insufficient funds");
                transferRepository.save(transfer);
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

        } catch (Exception e) {
            // Logging an error without throwing it to keep thread alive
            log.error("Failed to process transfer {}", transferId, e);

            try {
                Transfer transfer = transferRepository.findById(transferId).orElse(null);
                if (transfer != null && transfer.getStatus() == TransferStatus.PENDING) {
                    transfer.setStatus(TransferStatus.FAILED);
                    transfer.setReason("Internal error: " + e.getMessage());
                    transferRepository.save(transfer);
                }
            } catch (Exception saveEx) {
                log.error("Failed to update transfer status to FAILED", saveEx);
            }
        }
    }
}