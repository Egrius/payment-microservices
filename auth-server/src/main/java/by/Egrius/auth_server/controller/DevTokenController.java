package by.Egrius.auth_server.controller;

import by.Egrius.auth_server.entity.User;
import by.Egrius.auth_server.repository.UserRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Profile("dev")
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevTokenController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWKSource<SecurityContext> jwkSource;

    public record LoginRequest(String email, String password) {}

    @Value("${AUTH_SERVER_ISSUER:http://auth-server.local:9000}")
    private String authServerIssuer;

    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody LoginRequest req) {

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authServerIssuer)
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("public_id", user.getPublicId().toString())
                .claim("role", user.getRole().getAbbr())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));

        return ResponseEntity.ok(Map.of(
                "access_token", jwt.getTokenValue(),
                "token_type", "Bearer",
                "expires_in", 3600
        ));
    }
}
