package by.egrius.payment_service.components;

import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.event.TransferAddedEvent;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Profile("test")
@TestComponent
@Getter
@RequiredArgsConstructor
public class StatefulRetryTestListener {

    private final AtomicInteger invocationCount = new AtomicInteger();

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;

    @RabbitListener(queues = "processing.queue", containerFactory = "testRabbitListenerContainerFactory")
    @Transactional
    public void testListener(TransferAddedEvent event) {
        invocationCount.incrementAndGet();

        UUID randomUserId = UUID.randomUUID();

        Account from = accountRepository.save(Account.builder()
                .userId(randomUserId)
                .name("testAcc1")
                .balance(BigDecimal.ZERO)
                .currency("BYN")
                .build());

        Account to = accountRepository.save(Account.builder()
                .userId(randomUserId)
                .name("testAcc2")
                .balance(BigDecimal.ZERO)
                .currency("BYN")
                .build());

        transferRepository.save(Transfer.builder()
                .fromAccount(from)
                .toAccount(to)
                .amount(BigDecimal.TEN)
                .status(TransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        throw new RuntimeException("Test failure");
    }
}