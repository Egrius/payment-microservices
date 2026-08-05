package by.egrius.api_gateway.controller.oauth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OAuth2Controller {


    /*
    alice@example.com
    password123
     */
    @Autowired
    private OAuth2AuthorizedClientRepository authorizedClientRepository;


    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @GetMapping("/")
    public String mainPage(Authentication authentication) {
        System.out.println("=== Authentication ===");
        System.out.println("authentication: " + authentication);
        System.out.println("authentication class: " + (authentication != null ? authentication.getClass().getName() : "null"));
        System.out.println("isAuthenticated: " + (authentication != null ? authentication.isAuthenticated() : "null"));
        System.out.println("name: " + (authentication != null ? authentication.getName() : "null"));

        if (authentication == null) {
            return "Not authenticated - authentication is null";
        }

        return "Authenticated as: " + authentication.getName() +
                ", Authorities: " + authentication.getAuthorities();
    }

    @GetMapping("/token")
    public Map<String, Object> getAccessToken(Authentication authentication) {

        OAuth2AuthorizedClient authorizedClient =
                this.authorizedClientService.loadAuthorizedClient("auth-server", authentication.getName());

        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

        return Map.of(
                "access_token", accessToken.getTokenValue(),
                "token_type", accessToken.getTokenType().getValue(),
                "expires_at", accessToken.getExpiresAt() != null ? accessToken.getExpiresAt().toString() : "N/A",
                "client_registration_id", authorizedClient.getClientRegistration().getRegistrationId()
        );
    }

}
