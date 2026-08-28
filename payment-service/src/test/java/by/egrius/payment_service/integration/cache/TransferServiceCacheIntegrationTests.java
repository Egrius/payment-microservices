package by.egrius.payment_service.integration.cache;

import by.egrius.payment_service.context.ServiceIntegrationTestContext;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.event.TransferAddedEvent;
import by.egrius.payment_service.integration.config.BaseIntegrationTest;
import by.egrius.payment_service.integration.config.TestCacheConfig;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.service.CacheService;
import by.egrius.payment_service.service.TransferProcessor;
import by.egrius.payment_service.service.TransferService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(TestCacheConfig.class)
public class TransferServiceCacheIntegrationTests extends BaseIntegrationTest {
    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferProcessor transferProcessor;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoSpyBean
    private TransferRepository transferRepository;

    @MockitoSpyBean
    private CacheService cacheService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID fromUserId;
    private UUID toUserId;
    private Account fromAccount;
    private Account toAccount;
    private Transfer transfer;

    @BeforeEach
    void setUp() {
        // Очищаем кэш перед каждым тестом
        cacheManager.getCache("transfers").clear();

        fromUserId = UUID.randomUUID();
        toUserId = UUID.randomUUID();

        fromAccount = accountRepository.save(Account.builder()
                .publicId(UUID.randomUUID())
                .userId(fromUserId)
                .currency("USD")
                .name("FromAccount")
                .balance(BigDecimal.valueOf(500))
                .build());

        toAccount = accountRepository.save(Account.builder()
                .publicId(UUID.randomUUID())
                .userId(toUserId)
                .currency("USD")
                .name("ToAccount")
                .balance(BigDecimal.valueOf(500))
                .build()
        );

        transfer = transferRepository.save(Transfer.builder()
                .publicId(UUID.randomUUID())
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(BigDecimal.valueOf(150L))
                .status(TransferStatus.COMPLETED)
                .processedAt(LocalDateTime.now())
                .build()
        );
    }

    @SneakyThrows
    @Test
    void getTransferStatusShouldPutTransferInCache() {
        var cacheKey = fromUserId + "_" + transfer.getPublicId();
        var redisKey = "transfers::" + cacheKey;

        // Check the absence of value in cache
        assertThat(redisTemplate.hasKey(redisKey)).isFalse();
        assertThat(cacheManager.getCache("transfers").get(cacheKey)).isNull();

        // First call - cache miss
        TransferReadDto firstResult = transferService.getTransferStatus(
                transfer.getPublicId(),
                fromUserId
        );

        assertThat(redisTemplate.hasKey(redisKey)).isTrue();

        Object cached = redisTemplate.opsForValue().get(redisKey);
        assertThat(cached).isInstanceOf(TransferReadDto.class);

        TransferReadDto cachedDto = (TransferReadDto) cached;
        assertThat(cachedDto.publicId()).isEqualTo(transfer.getPublicId());
        assertThat(cachedDto.status()).isEqualTo(TransferStatus.COMPLETED);

        Long ttl = redisTemplate.getExpire(redisKey);
        assertThat(ttl).isBetween(290L, 310L);

        TransferReadDto secondResult = transferService.getTransferStatus(
                transfer.getPublicId(),
                fromUserId
        );

        assertThat(secondResult).isEqualTo(firstResult);
        assertThat(secondResult).isEqualTo(cachedDto);

        verify(transferRepository, times(1))
                .findByTransferPublicId_and_UserPublicId(
                        transfer.getPublicId(),
                        fromUserId
                );

        transferRepository.delete(transfer);

        TransferReadDto thirdResult = transferService.getTransferStatus(
                transfer.getPublicId(),
                fromUserId
        );

        assertThat(thirdResult).isEqualTo(cachedDto);

        verify(transferRepository, times(1))
                .findByTransferPublicId_and_UserPublicId(any(), any());
    }

    @Test
    void createTransferShouldPutInCache() {
        TransferCreateDto createDto = new TransferCreateDto(fromAccount.getPublicId(), toAccount.getPublicId(), new BigDecimal(200));

        assertTrue(redisTemplate.keys("transfers::*").isEmpty());

        BigDecimal fromAccountBalanceBefore = fromAccount.getBalance();
        BigDecimal toAccountBalanceBefore = toAccount.getBalance();

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(cacheService, times(1)).put(any(), any(), any());
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));

        assertFalse(redisTemplate.keys("transfers::*").isEmpty());

        var key = fromUserId + "_" + createdTransfer.publicId();
        var redisKey = "transfers::" + key;
        Object cached = redisTemplate.opsForValue().get(redisKey);
        assertThat(cached).isNotNull();
        assertThat(cached).isInstanceOf(TransferReadDto.class);

        TransferReadDto cachedDto = (TransferReadDto) cached;
        assertThat(cachedDto.publicId()).isEqualTo(createdTransfer.publicId());
        assertThat(cachedDto.amount()).isEqualTo(new BigDecimal(200));
        assertThat(cachedDto.status()).isEqualTo(TransferStatus.PENDING);

        TransferReadDto gotFromCache = transferService.getTransferStatus(
                createdTransfer.publicId(),
                fromUserId
        );

        assertThat(gotFromCache).isEqualTo(createdTransfer);
        assertThat(gotFromCache).isEqualTo(cachedDto);

        verify(transferRepository, never()).findByTransferPublicId_and_UserPublicId(any(), any());
    }

    @Test
    void processTransferShouldUpdateCacheToCompleted() throws Exception {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(150L)
        );

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(),
                anyString(),
                eventCaptor.capture()
        );

        TransferAddedEvent addedEvent = eventCaptor.getValue();
        assertNotNull(addedEvent);

        var key = fromUserId + "_" + createdTransfer.publicId();
        var redisKey = "transfers::" + key;

        Object cached = redisTemplate.opsForValue().get(redisKey);
        assertThat(cached).isNotNull();
        TransferReadDto cachedDto = (TransferReadDto) cached;
        assertThat(cachedDto.status()).isEqualTo(TransferStatus.PENDING);

        transferProcessor.processTransfer(addedEvent);

        Object updatedCached = redisTemplate.opsForValue().get(redisKey);
        assertThat(updatedCached).isNotNull();
        TransferReadDto updatedDto = (TransferReadDto) updatedCached;
        assertThat(updatedDto.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(updatedDto.processedAt()).isNotNull();

        verify(cacheService, times(2)).put(eq("transfers"), eq(key), any(TransferReadDto.class));
    }

    @Test
    void duplicateProcessingShouldNotUpdateCache() throws Exception {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(150L)
        );

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(),
                anyString(),
                eventCaptor.capture()
        );

        TransferAddedEvent addedEvent = eventCaptor.getValue();
        assertNotNull(addedEvent);

        var key = fromUserId + "_" + createdTransfer.publicId();
        var redisKey = "transfers::" + key;

        transferProcessor.processTransfer(addedEvent);

        Object cached = redisTemplate.opsForValue().get(redisKey);
        assertThat(cached).isNotNull();
        TransferReadDto cachedDto = (TransferReadDto) cached;
        assertThat(cachedDto.status()).isEqualTo(TransferStatus.COMPLETED);

        clearInvocations(cacheService);

        transferProcessor.processTransfer(addedEvent);

        verify(cacheService, never()).put(eq("transfers"), eq(key), any(TransferReadDto.class));

        Object afterDuplicate = redisTemplate.opsForValue().get(redisKey);
        assertThat(afterDuplicate).isNotNull();
        TransferReadDto afterDto = (TransferReadDto) afterDuplicate;
        assertThat(afterDto.status()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void concurrentCacheReadsShouldReturnSameResult() throws Exception {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(150L)
        );

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(),
                anyString(),
                eventCaptor.capture()
        );

        TransferAddedEvent addedEvent = eventCaptor.getValue();
        assertNotNull(addedEvent);

        transferProcessor.processTransfer(addedEvent);

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        var results = new TransferReadDto[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results[index] = transferService.getTransferStatus(
                            createdTransfer.publicId(),
                            fromUserId
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        for (int i = 1; i < threadCount; i++) {
            assertThat(results[i]).isEqualTo(results[0]);
            assertThat(results[i].status()).isEqualTo(TransferStatus.COMPLETED);
        }

        verify(transferRepository, times(0))
                .findByTransferPublicId_and_UserPublicId(any(), any());
    }

    @Test
    void failedTransferShouldUpdateCacheToFailed() throws Exception {

        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(2000L)
        );

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(),
                anyString(),
                eventCaptor.capture()
        );

        TransferAddedEvent addedEvent = eventCaptor.getValue();
        assertNotNull(addedEvent);

        var key = fromUserId + "_" + createdTransfer.publicId();
        var redisKey = "transfers::" + key;

        Object cached = redisTemplate.opsForValue().get(redisKey);
        assertThat(cached).isNotNull();
        TransferReadDto cachedDto = (TransferReadDto) cached;
        assertThat(cachedDto.status()).isEqualTo(TransferStatus.PENDING);

        transferProcessor.processTransfer(addedEvent);

        Object updatedCached = redisTemplate.opsForValue().get(redisKey);
        assertThat(updatedCached).isNull();

        Transfer transferFromDBAfterFailure = transferRepository.findByTransferPublicId_and_UserPublicId(
                cachedDto.publicId(), fromAccount.getUserId())
                .orElseThrow();

        assertThat(transferFromDBAfterFailure.getStatus()).isEqualTo(TransferStatus.FAILED);

        verify(cacheService, times(1)).put(eq("transfers"), eq(key), any(TransferReadDto.class));

        verify(cacheService, times(1)).evict(eq("transfers"), eq(key));
    }

    @Test
    void failedTransferShouldEvictCache() throws Exception {
        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(2000L)
        );

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(),
                anyString(),
                eventCaptor.capture()
        );

        TransferAddedEvent addedEvent = eventCaptor.getValue();
        assertNotNull(addedEvent);

        var key = fromUserId + "_" + createdTransfer.publicId();
        var redisKey = "transfers::" + key;

        transferProcessor.processTransfer(addedEvent);

        verify(cacheService, times(1)).evict(eq("transfers"), eq(key));

        verify(cacheService, times(1)).put(eq("transfers"), eq(key), any(TransferReadDto.class));

        Object cached = redisTemplate.opsForValue().get(redisKey);
        assertThat(cached).isNull();

        Transfer transferFromDBAfterFailure = transferRepository.findByTransferPublicId_and_UserPublicId(
                        createdTransfer.publicId(), fromAccount.getUserId())
                .orElseThrow();
    }

    @Test
    void cacheTTLShouldBeCorrect() throws Exception {

        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(150L)
        );

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(),
                anyString(),
                eventCaptor.capture()
        );

        TransferAddedEvent addedEvent = eventCaptor.getValue();
        assertNotNull(addedEvent);

        var key = fromUserId + "_" + createdTransfer.publicId();
        var redisKey = "transfers::" + key;

        Long pendingTtl = redisTemplate.getExpire(redisKey);
        assertThat(pendingTtl).isBetween(290L, 310L);

        transferProcessor.processTransfer(addedEvent);

        Long completedTtl = redisTemplate.getExpire(redisKey);
        assertThat(completedTtl).isBetween(290L, 310L);
    }

    @Test
    void getTransferStatusFromCacheShouldNotCallDatabase() throws Exception {

        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(150L)
        );

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(),
                anyString(),
                eventCaptor.capture()
        );

        TransferAddedEvent addedEvent = eventCaptor.getValue();
        assertNotNull(addedEvent);

        transferProcessor.processTransfer(addedEvent);

        clearInvocations(transferRepository);

        for (int i = 0; i < 5; i++) {
            transferService.getTransferStatus(createdTransfer.publicId(), fromUserId);
        }

        verify(transferRepository, never())
                .findByTransferPublicId_and_UserPublicId(any(), any());
    }

    @Test
    void duplicateProcessingShouldNotModifyCache() throws Exception {

        TransferCreateDto createDto = new TransferCreateDto(
                fromAccount.getPublicId(),
                toAccount.getPublicId(),
                BigDecimal.valueOf(150L)
        );

        ArgumentCaptor<TransferAddedEvent> eventCaptor = ArgumentCaptor.forClass(TransferAddedEvent.class);

        TransferReadDto createdTransfer = transferService.createTransfer(createDto, fromUserId);

        verify(rabbitTemplate, times(1)).convertAndSend(
                anyString(),
                anyString(),
                eventCaptor.capture()
        );

        TransferAddedEvent addedEvent = eventCaptor.getValue();
        assertNotNull(addedEvent);

        var key = fromUserId + "_" + createdTransfer.publicId();
        var redisKey = "transfers::" + key;

        transferProcessor.processTransfer(addedEvent);

        Object cachedBefore = redisTemplate.opsForValue().get(redisKey);
        assertThat(cachedBefore).isNotNull();
        TransferReadDto beforeDuplicate = (TransferReadDto) cachedBefore;
        assertThat(beforeDuplicate.status()).isEqualTo(TransferStatus.COMPLETED);


        clearInvocations(cacheService);

        transferProcessor.processTransfer(addedEvent);

        verify(cacheService, never()).put(eq("transfers"), eq(key), any(TransferReadDto.class));

        Object cachedAfter = redisTemplate.opsForValue().get(redisKey);
        assertThat(cachedAfter).isNotNull();
        TransferReadDto afterDuplicate = (TransferReadDto) cachedAfter;

        assertThat(beforeDuplicate).isEqualTo(afterDuplicate);
    }
}