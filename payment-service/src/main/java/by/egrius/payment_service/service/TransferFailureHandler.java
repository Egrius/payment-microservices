package by.egrius.payment_service.service;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.event.TransferProcessedEvent;
import by.egrius.payment_service.exception.payment_service.TransferProcessingException;
import by.egrius.payment_service.mapper.TransferMapper;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.utils.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferFailureHandler {

    private final TransferRepository transferRepository;
    private final TransferMapper transferMapper;
    private final CacheService cacheService;
    private final RabbitTemplate rabbitTemplate;

    private static final String TRANSFERS_CACHE_NAME = "transfers";
    private static final String EXCHANGE_NAME = "payment.exchange";
    private static final String ROUTING_KEY = "transfer.completed";

    @Transactional(propagation = Propagation.REQUIRED)
    public void handle(long transferId, String reason) {
        try {
            Transfer transfer = transferRepository.findById(transferId).orElse(null);
            if (transfer == null) {
                log.error("Transfer {} not found during failure handling", transferId);
                return;
            }

            if (transfer.getStatus() != TransferStatus.PENDING) {
                log.warn("Transfer {} already in status {}, skipping failure handling",
                        transferId, transfer.getStatus());
                return;
            }

            transfer.setStatus(TransferStatus.FAILED);
            transfer.setReason(reason);
            transfer.setProcessedAt(LocalDateTime.now());
            transferRepository.save(transfer);

            UUID userId = transfer.getFromAccount().getUserId();
            String key = CacheService.generateKey(userId.toString(), transfer.getPublicId().toString());
            cacheService.evict(TRANSFERS_CACHE_NAME, key);

            TransferReadDto dto = transferMapper.mapToReadDto(transfer);

            TransactionUtils.afterCommit(() -> publish(userId, dto));

            log.info("Transfer {} marked as FAILED: {}", transferId, reason);

        } catch (Exception e) {
            log.error("Failed to update transfer {} status to FAILED", transferId, e);
            throw new TransferProcessingException(transferId, "Failed to update transfer status", e);
        }
    }

    private void publish(UUID userId, TransferReadDto dto) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, new TransferProcessedEvent(userId, dto));
        } catch (Exception e) {
            log.error("Failed to publish TransferProcessedEvent for transfer {}", dto.publicId(), e);
        }
    }
}