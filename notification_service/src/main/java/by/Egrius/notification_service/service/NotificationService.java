package by.Egrius.notification_service.service;

import by.Egrius.notification_service.entity.Notification;
import by.Egrius.notification_service.entity.NotificationStatus;
import by.Egrius.notification_service.event.TransferProcessedEvent;
import by.Egrius.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationService {

    private static final int BATCH_SIZE = 10;

    private final NotificationRepository notificationRepository;

    private final SseEmitterStorageService sseEmitterStorageService;

    public void createNotificationForPaymentProcessedEventAndSendToUser(TransferProcessedEvent event) {

        boolean sentResult = sseEmitterStorageService.send(event.getUserPublicTd().toString(), event);

        NotificationStatus status = (sentResult) ? NotificationStatus.SENT : NotificationStatus.PENDING;

        Map<String, Object> payload = Map.of(
                "transferPublicId", event.getTransferPublicId(),
                "amount", event.getAmount(),
                "userId", event.getUserPublicTd(),
                "fromAccountPublicId", event.getFromAccountPublicId(),
                "toAccountPublicId", event.getToAccountPublicId(),
                "status", event.getStatus(),
                "processedAt", LocalDateTime.now().toString()
        );

        Notification notification = Notification.builder()
                .status(status)
                .userId(event.getUserPublicTd())
                .eventType("TRANSFER_PROCESSED")
                .createdAt(LocalDateTime.now())
                .payload(payload)
                .build();

        notificationRepository.save(notification);
    }

    // No transaction bc we want to send at least something before fail
    // TransactionalTemplate could be used for each batch to stay consistent, but I decided to keep it simple
    // since it's just a notification
    public void sendAllPendingNotificationsToUser(String userId) {
        List<Notification> pendingNotifications = notificationRepository
                .findAllByStatusAndUserIdWithDateOrdering(UUID.fromString(userId), NotificationStatus.PENDING);

        if(pendingNotifications.isEmpty()) return;

        SseEmitter emitter = sseEmitterStorageService.get(userId);
        if(emitter == null) {
            log.debug("No emitter for user {} - pending will be sent on next connect", userId);
            return;
        }

        List<Notification> batch;

        for (int i = 0; i < pendingNotifications.size(); i += BATCH_SIZE) {

            batch = pendingNotifications.subList(i, Math.min(i + BATCH_SIZE, pendingNotifications.size()));

            for (Notification notification : batch) {
                if (!sseEmitterStorageService.send(userId, notification)) return;

                notification.setStatus(NotificationStatus.SENT);
            }
            notificationRepository.saveAll(batch);
        }
    }
}