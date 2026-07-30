package by.egrius.api_gateway.dto.user;

import java.util.UUID;

public record  CurrentUserDto (
    String subject,
    String email,
    String username,
    UUID publicId
) {
    public static CurrentUserDto fromJwt(org.springframework.security.oauth2.jwt.Jwt jwt) {
        return new CurrentUserDto(
                jwt.getSubject(),
                jwt.getClaim("email"),
                jwt.getClaim("username"),
                UUID.fromString(jwt.getClaim("public_id"))
        );
    }
}
