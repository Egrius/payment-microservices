package by.egrius.payment_service.controller.test;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/test")
@Profile("integration")
public class TestAuthController {

    private final JwtEncoder jwtEncoder;

    public TestAuthController(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @PostMapping("/token")
    public Map<String, String> getTestToken() {

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://payment-service.local:8080")
                .subject("test@example.com")
                .claim("email", "test@example.com")
                .claim("username", "testuser")
                .claim("public_id", "ae22624a-06f6-428c-afec-893fb3b3f448")
                .claim("roles", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(claims));

        return Map.of("access_token", jwt.getTokenValue());
    }
}