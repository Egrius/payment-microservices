package by.egrius.payment_service.controller.oauth;

import by.egrius.payment_service.dto.request.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final RestTemplate restTemplate;

    @Value("http://${auth.server.host:auth-server.local}:${auth.server.port:9000}")
    private String authServerUrl;

    @GetMapping("/login")
    public String login() {
        return "redirect:/oauth2/authorization/auth-server";
   }

   @PostMapping("/register")
   @ResponseBody
   public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
       try {
           ResponseEntity<String> response = restTemplate.postForEntity(
                   authServerUrl + "/api/register", request, String.class);

           return ResponseEntity
                   .status(response.getStatusCode())
                   .body(Map.of("response", bodyOrEmpty(response.getBody())));

       } catch (HttpClientErrorException e) {
           log.warn("Client error from auth-server: status={}, body={}",
                   e.getStatusCode(), e.getResponseBodyAsString());

           return ResponseEntity
                   .status(e.getStatusCode())
                   .body(Map.of("error", bodyOrEmpty(e.getResponseBodyAsString())));

       } catch (Exception e) {
           log.error("Error calling auth-server", e);

           return ResponseEntity
                   .status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(Map.of("error", "Internal Server Error"));
       }
   }

    private static String bodyOrEmpty(String body) {
        return body != null ? body : "";
    }
}