package by.egrius.payment_service.controller.oauth;

import by.egrius.payment_service.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final RestTemplate restTemplate;

    @Value("${auth.server.host:auth-server.local}")
    private String authServerHost;

    @Value("${auth.server.port:9000}")
    private String authServerPort;

    @GetMapping("/login")
    public String login() {
        return "redirect:/oauth2/authorization/auth-server";
   }

   @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return restTemplate.postForEntity("http://" + authServerHost + ":" + authServerPort + "/api/register",  request, String.class);
   }
}

