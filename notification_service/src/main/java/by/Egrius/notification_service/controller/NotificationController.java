package by.Egrius.notification_service.controller;

import by.Egrius.notification_service.dto.response.ErrorResponse;
import by.Egrius.notification_service.dto.subscription.SubscriptionReadDto;
import by.Egrius.notification_service.exception.InvalidUserIdException;
import by.Egrius.notification_service.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Tag(name = "Subscriptions", description = "SSE-subscriptions for notifications")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class NotificationController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "Create subscription",
            description = "Creates an SSE subscription for the current user. " +
                    "Idempotent — returns the existing subscription if already present.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subscription created"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid JWT claims",   content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",   content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<SubscriptionReadDto> createSubscription(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        String email = extractEmail(jwt);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(subscriptionService.createSubscription(userId, email));
    }

    @Operation(summary = "Listen to notifications (SSE)",
            description = """
                       Opens a Server-Sent Events stream for the current user.
                       The client receives real-time notifications.
                       Keep the connection open; reconnect on failure.

                       ⚠️ Use curl or a real frontend client to test.
                       """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened",
                    content = @Content(mediaType = "text/event-stream")),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public SseEmitter listenSubscription(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.createSseForSubscription(extractUserId(jwt));
    }

    @Operation(summary = "Delete subscription",
            description = "Unsubscribes the current user and closes the SSE connection.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Subscription deleted"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid JWT claims", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteSubscription(@AuthenticationPrincipal Jwt jwt) {
        boolean deleted = subscriptionService.unsubscribeAndDeleteSse(extractUserId(jwt).toString());

        return ResponseEntity.noContent().build();
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

    private String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidUserIdException("email claim is missing in token");
        }
        return email;
    }
}