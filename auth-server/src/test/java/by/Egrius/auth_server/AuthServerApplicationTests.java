package by.Egrius.auth_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnableAutoConfiguration(exclude = {OAuth2AuthorizationServerAutoConfiguration.class})
class AuthServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
