package by.egrius.payment_service.controller.oauth;

import by.egrius.payment_service.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
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

       try {
           return restTemplate.postForEntity("http://" + authServerHost + ":" + authServerPort + "/api/register",  request, String.class);

       } catch (HttpClientErrorException.Conflict e) {

           log.warn("User already exists: {}", e.getResponseBodyAsString());
           return ResponseEntity
                   .status(HttpStatus.CONFLICT)
                   .body(e.getResponseBodyAsString());
       } catch (HttpClientErrorException e) {

           log.error("Client error from auth-server: {}", e.getResponseBodyAsString());
           return ResponseEntity
                   .status(e.getStatusCode())
                   .body(e.getResponseBodyAsString());
       } catch (Exception e) {

           log.error("Error calling auth-server: ", e);
           return ResponseEntity
                   .status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body("{\"error\":\"Internal Server Error\"}");
       }
    }
}