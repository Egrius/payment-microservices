package by.egrius.payment_service.controller.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OAuth2Controller {

    private static final String REGISTRATION_ID = "auth-server";

    private final OAuth2AuthorizedClientService authorizedClientService;

    @GetMapping("/")
    public String mainPage(Authentication authentication) {
        if (authentication == null) {
            return "Not authenticated";
        }
        return "Authenticated as: " + authentication.getName()
                + ", Authorities: " + authentication.getAuthorities();
    }

    /**
     * Debug endpoint to inspect the current OAuth2 access token.
     * Not intended for production use.
     */
    @GetMapping("/token")
    public Map<String, Object> getAccessToken(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        OAuth2AuthorizedClient authorizedClient = authorizedClientService
                .loadAuthorizedClient(REGISTRATION_ID, authentication.getName());

        if (authorizedClient == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "No authorized client for user " + authentication.getName());
        }

        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

        Map<String, Object> result = new HashMap<>();
        result.put("access_token", accessToken.getTokenValue());
        result.put("token_type", accessToken.getTokenType().getValue());
        result.put("expires_at", accessToken.getExpiresAt() != null
                ? accessToken.getExpiresAt().toString()
                : null);
        result.put("client_registration_id", authorizedClient.getClientRegistration().getRegistrationId());
        result.put("refresh_token", authorizedClient.getRefreshToken() != null
                ? authorizedClient.getRefreshToken().getTokenValue()
                : null);

        return result;
    }
}