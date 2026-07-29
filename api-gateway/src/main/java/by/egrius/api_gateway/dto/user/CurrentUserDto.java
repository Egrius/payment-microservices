package by.egrius.api_gateway.dto.user;

public record  CurrentUserDto (
    String subject,
    String email,
    String username,
    String publicId
) {
    public static CurrentUserDto fromJwt(org.springframework.security.oauth2.jwt.Jwt jwt) {
        return new CurrentUserDto(
                jwt.getSubject(),
                jwt.getClaim("email"),
                jwt.getClaim("username"),
                jwt.getClaim("public_id")
        );
    }
}
