package by.egrius.payment_service.integration.authorization.config;

import by.egrius.payment_service.integration.config.TestCacheConfig;
import by.egrius.payment_service.integration.config.TestRabbitMQConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;

@Configuration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
                "by.egrius.payment_service.components",
                "by.egrius.payment_service.service",
                "by.egrius.payment_service.repository",
                "by.egrius.payment_service.mapper",
                "by.egrius.payment_service.event",
                "by.egrius.payment_service.entity",
                "by.egrius.payment_service.dto",
                "by.egrius.payment_service.exception",
                "by.egrius.payment_service.exception.payment_service"
        },
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "by\\.egrius\\.payment_service\\.config\\..*"
                ),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "by\\.egrius\\.payment_service\\.controller\\..*"
                )
        }
)
@EntityScan(basePackages = "by.egrius.payment_service.entity")
@EnableJpaRepositories(basePackages = "by.egrius.payment_service.repository")
@EnableTransactionManagement
@EnableAsync
public class AuthIntegrationTestContext {
}
