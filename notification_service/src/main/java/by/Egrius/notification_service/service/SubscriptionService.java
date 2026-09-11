package by.Egrius.notification_service.service;

import by.Egrius.notification_service.dto.subscription.SubscriptionCreateDto;
import by.Egrius.notification_service.dto.subscription.SubscriptionReadDto;
import by.Egrius.notification_service.entity.Subscription;
import by.Egrius.notification_service.event.TransferProcessedEvent;
import by.Egrius.notification_service.exception.SubscriptionNotFoundException;
import by.Egrius.notification_service.mapper.SubscriptionMapper;
import by.Egrius.notification_service.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;

    private final SubscriptionRepository subscriptionRepository;

    private final NotificationService notificationService;
    private final SseEmitterStorageService sseEmitterStorageService;

    private static final long SSE_EMITTER_TIMEOUT = 0L;

    @Transactional
    public SubscriptionReadDto createSubscription(SubscriptionCreateDto createDto) {

        String userId = createDto.userId();

        Subscription subscription = subscriptionRepository.findLatestActiveByUserId(UUID.fromString(userId))
                .orElseGet(() -> new Subscription(
                        UUID.fromString(createDto.userId()),
                        createDto.userEmail(),
                        createDto.message()
                )
        );

        return subscriptionMapper.toReadDto(subscriptionRepository.save(subscription));
    }

    public SseEmitter createSseForSubscription(UUID userId) {

        log.debug("Creating SSE emitter for user: {}", userId);

        subscriptionRepository.findLatestActiveByUserId(userId)
                .orElseThrow(() -> new SubscriptionNotFoundException(userId.toString()));

        sseEmitterStorageService.remove(userId.toString());

        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT);
        sseEmitterStorageService.put(userId.toString(), emitter);

        try{
            notificationService.sendAllPendingNotificationsToUser(userId.toString());
        } catch (Exception e) {
            log.error("Failed to send pending notifications for user {}", userId, e);
            sseEmitterStorageService.remove(userId.toString());
            emitter.completeWithError(e);
            throw e;
        }

        emitter.onCompletion(() -> {
            sseEmitterStorageService.remove(userId.toString());
            log.debug("SSE completed for user: {}", userId);
        });

        emitter.onTimeout(() -> {
            sseEmitterStorageService.remove(userId.toString());
            log.debug("SSE timeout for user: {}", userId);
        });

        emitter.onError((ex) -> {
            sseEmitterStorageService.remove(userId.toString());
            log.error("SSE error for user: {}", userId, ex);
        });

        return emitter;
    }

    @Transactional
    public boolean unsubscribeAndDeleteSse(String userId) {

        Optional<Subscription> subscription = subscriptionRepository.findLatestActiveByUserId(UUID.fromString(userId));

       if(subscription.isEmpty()) {
           // It may be desynchronized state when we have sse opened in the map, and there is no active subscription in DB
           sseEmitterStorageService.remove(userId);
           throw  new RuntimeException("Could not find latest active subscription");
       }

        int updated = subscriptionRepository.setSubscriptionStatus(subscription.get().getId(), false);

        sseEmitterStorageService.remove(userId);

        return updated == 1;
    }

    @RabbitListener(queues = "notification.queue")
    @Transactional
    public void handlePaymentEvent(TransferProcessedEvent event) {

        log.info("Received payment event for user: {}", event.getUserPublicTd());
        notificationService.createNotificationForPaymentProcessedEventAndSendToUser(event);
    }
}