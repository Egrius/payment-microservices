package by.Egrius.auth_server.dto.user;

import java.util.UUID;

public record UserReadDto(
        UUID publicId,
        String username,
        String email
) { }
