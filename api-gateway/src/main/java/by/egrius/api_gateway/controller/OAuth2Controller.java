package by.egrius.api_gateway.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuth2Controller {

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
}
