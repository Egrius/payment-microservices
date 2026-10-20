package by.egrius.payment_service.controller.test;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Tag(
        name = "Dev - Auth",
        description = "Dev-only helpers for smoke testing. Not available in production."
)
@Profile("dev")
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevTokenProxyController {

    private final RestClient restClient;

    @Value("${auth.server.host:auth-server.local}")
    private String authServerHost;

    @Value("${auth.server.port:9000}")
    private String authServerPort;

    @Schema(description = "Credentials of a pre-seeded test user")
    public record LoginRequest(
            @Schema(description = "User email", example = "smoke@test.com")
            String email,

            @Schema(description = "User password", example = "password123")
            String password
    ) {}

    @Schema(description = "Access token issued by auth-server")
    public record TokenResponse(
            @Schema(description = "JWT access token", example = "eyJraWQiOi...")
            String access_token,

            @Schema(description = "Token type", example = "Bearer")
            String token_type,

            @Schema(description = "Token TTL in seconds", example = "3600")
            long expires_in
    ) {}

    @Operation(
            summary = "Get a JWT for the test user (dev only)",
            description = """
            Proxies the request to `auth-server`'s `POST /dev/token` and returns the JWT.
            Use the returned `access_token` with the **Authorize** button (without the `Bearer ` prefix).

            The endpoint is available only with the `dev` profile and requires no authentication.
            It avoids the OAuth2 authorization code flow - it simply checks email/password 
            and issues a signed JWT with the same claims.
            """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "JWT issued",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid email or password",
                    content = @Content
            )
    })
    @SecurityRequirements
    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody LoginRequest req) {
        try {
            String url = "http://" + authServerHost + ":" + authServerPort + "/dev/token";

            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(Map.class);
            return ResponseEntity.ok(response);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAs(Map.class));
        }
    }
}