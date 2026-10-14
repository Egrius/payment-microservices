package by.egrius.payment_service.service;

import by.egrius.payment_service.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyCleanupScheduler {
    private final IdempotencyRepository idempotencyRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredKeys() {
        int deleted = idempotencyRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Deleted {} expired idempotency keys", deleted);
    }
}