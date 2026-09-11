package by.Egrius.auth_server.controller;

import by.Egrius.auth_server.dto.request.RegisterRequest;
import by.Egrius.auth_server.entity.RoleName;
import by.Egrius.auth_server.entity.User;
import by.Egrius.auth_server.exception.UserEmailAlreadyExistsException;
import by.Egrius.auth_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {

        userRepository.findByEmail(request.email())
                .ifPresent(user -> {
                    throw new UserEmailAlreadyExistsException(request.email());
                });

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .role(RoleName.USER)
                .build();

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("User registered successfully");
    }
}