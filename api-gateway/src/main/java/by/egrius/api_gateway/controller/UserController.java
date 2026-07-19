package by.egrius.api_gateway.controller;

import by.egrius.api_gateway.dto.user.UserCreateDto;
import by.egrius.api_gateway.dto.user.UserReadDto;
import by.egrius.api_gateway.dto.user.UserUpdateDto;
import by.egrius.api_gateway.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserReadDto> createUser(@Valid @RequestBody UserCreateDto dto) {
        UserReadDto user = userService.createUser(dto);
        return ResponseEntity
                .created(URI.create("/api/users/" + user.publicId()))
                .body(user);
    }

    @GetMapping("/{publicId}")
    public UserReadDto getUser(@PathVariable UUID publicId) {
        return userService.getUserByPublicId(publicId);
    }

    @GetMapping
    public List<UserReadDto> getAllUsers() {
        return userService.getAllUsers();
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<UserReadDto> updateUser(
            @PathVariable UUID publicId,
            @Valid @RequestBody UserUpdateDto dto
    ) {
        UserReadDto user = userService.updateUser(publicId, dto);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID publicId) {
        userService.deleteUser(publicId);
        return ResponseEntity.noContent().build();
    }
}