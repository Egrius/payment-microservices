package by.egrius.api_gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuth2CallbackController {

    @GetMapping("/login/oauth2/code/oidc-client")
    public String handleCallback(@RequestParam("code") String code,
                                 @RequestParam(value = "state", required = false) String state) {
        // Этот код ты получил от auth-server
        System.out.println("🔑 Code received: " + code);
        System.out.println("🔒 State: " + state);

        // Здесь будет логика обмена code на token
        return "Code received! Now exchange it for token.";
    }
}