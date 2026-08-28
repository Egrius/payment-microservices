package by.Egrius.notification_service.controller;

import by.Egrius.notification_service.dto.SubscriptionCreateDto;
import by.Egrius.notification_service.dto.SubscriptionReadDto;
import by.Egrius.notification_service.service.NotificationService;
import by.Egrius.notification_service.service.SubscriptionService;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class NotificationController {

    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<SubscriptionReadDto> createSubscription(
            @RequestBody SubscriptionCreateDto createDto
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(subscriptionService.createSubscription(createDto));
    }

    // TODO make JWT token verification on userId
    /*
        UUID on controller's method made to throw an exception if UUID format is incorrect
        I may added dto with @Valid but it's get method, so there is no place for requestBody
     */
    @GetMapping("/{userId}")
    public SseEmitter listenSubscription(@PathVariable("userId") UUID userId) {
        return subscriptionService.createSseForSubscription(userId.toString());
    }

    @DeleteMapping(path = "/unsubscribe/{userId}")
    public ResponseEntity<java.util.Map<String, Boolean>> deleteSubscription(@PathVariable("userId") UUID userId ) {
        boolean deleted = subscriptionService.unsubscribeAndDeleteSse(userId.toString());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(java.util.Map.of("deleted", deleted));
    }
}