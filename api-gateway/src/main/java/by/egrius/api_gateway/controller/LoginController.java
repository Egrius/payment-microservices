package by.egrius.api_gateway.controller;

import lombok.RequiredArgsConstructor;
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

    @GetMapping("/login")
    public String login() {
        return "redirect:/oauth2/authorization/auth-server";
   }

   @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return restTemplate.postForEntity("http://auth-server.local:9000/api/register",  request, String.class);
   }
}

record RegisterRequest(String username, String password, String email) {}
