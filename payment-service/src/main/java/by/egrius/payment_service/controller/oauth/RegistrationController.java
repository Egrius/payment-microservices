package by.egrius.payment_service.controller.oauth;

import by.egrius.payment_service.dto.request.RegisterRequest;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@Hidden
@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

    @Value("${auth.server.host:auth-server}")
    private String authServerHost;

    @Value("${auth.server.port:9000}")
    private String authServerPort;

    private final RestTemplate restTemplate;

    @Operation(description = "Proxy to auth-server for convenience")
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        String url = "http://" + authServerHost + ":" + authServerPort + "/api/register";

        restTemplate.postForEntity(url, request, String.class);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}