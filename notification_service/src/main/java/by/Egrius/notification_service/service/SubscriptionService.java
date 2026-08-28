package by.Egrius.notification_service.service;

import by.Egrius.notification_service.dto.SubscriptionCreateDto;
import by.Egrius.notification_service.dto.SubscriptionReadDto;
import by.Egrius.notification_service.entity.Notification;
import by.Egrius.notification_service.entity.NotificationStatus;
import by.Egrius.notification_service.entity.Subscription;
import by.Egrius.notification_service.event.TransferProcessedEvent;
import by.Egrius.notification_service.mapper.SubscriptionMapper;
import by.Egrius.notification_service.repository.NotificationRepository;
import by.Egrius.notification_service.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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

    // TODO Add logic to send all the pending notifications to the user and change them to SENT

    public SseEmitter createSseForSubscription(String userId) {

        // TODO custom exception
        Subscription subscription = subscriptionRepository.findLatestActiveByUserId(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("No active subscriptions were found"));

        sseEmitterStorageService.remove(userId);

        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT);

        sseEmitterStorageService.put(userId, emitter);

        notificationService.sendAllPendingNotificationsToUser(userId);

        emitter.onCompletion(() -> {
            sseEmitterStorageService.remove(userId);
            log.debug("Emitter completed for user: {}", userId);
        });

        emitter.onTimeout(() -> {
            sseEmitterStorageService.remove(userId);
            log.debug("Emitter timeout for user: {}", userId);
        });

        emitter.onError((ex) -> {
            sseEmitterStorageService.remove(userId);
            log.debug("Emitter error for user: {}", userId, ex);
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