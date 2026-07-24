package by.Egrius.auth_server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "spring.security.oauth2.authorizationserver.jwt.jwk-source.key-store")
@Component
@Getter
@Setter
public class KeyStoreProperties {
    private String path;
    private String password;
    private String alias;
}
