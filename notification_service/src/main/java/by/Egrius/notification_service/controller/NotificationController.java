package by.Egrius.notification_service.controller;

import by.Egrius.notification_service.dto.subscription.SubscriptionCreateDto;
import by.Egrius.notification_service.dto.subscription.SubscriptionReadDto;
import by.Egrius.notification_service.exception.InvalidUserIdException;
import by.Egrius.notification_service.service.NotificationService;
import by.Egrius.notification_service.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class NotificationController {

    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<SubscriptionReadDto> createSubscription(
            @Valid @RequestBody SubscriptionCreateDto createDto
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(subscriptionService.createSubscription(createDto));
    }

    @GetMapping
    public SseEmitter listenSubscription(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.createSseForSubscription(extractUserId(jwt));
    }

    @DeleteMapping(path = "/unsubscribe")
    public ResponseEntity<Map<String, Boolean>> deleteSubscription(@AuthenticationPrincipal Jwt jwt) {
        boolean deleted = subscriptionService.unsubscribeAndDeleteSse(extractUserId(jwt).toString());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of("deleted", deleted));
    }

    private UUID extractUserId(Jwt jwt) {
        String publicId = jwt.getClaimAsString("public_id");

        if (publicId == null || publicId.isBlank()) {
            throw new InvalidUserIdException("public_id claim is missing in token");
        }

        try {
            return UUID.fromString(publicId);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserIdException("public_id claim is not a valid UUID: " + publicId);
        }
    }
}