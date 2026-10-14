package by.egrius.payment_service.controller.oauth;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden
@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    @Operation(description = "Proxy to auth-server for convenience")
    @GetMapping("/login")
    public String login() {
        return "redirect:/oauth2/authorization/auth-server";
   }

    private static String bodyOrEmpty(String body) {
        return body != null ? body : "";
    }
}