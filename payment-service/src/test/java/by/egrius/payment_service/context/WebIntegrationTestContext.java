package by.egrius.payment_service.context;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class,
        ReactiveOAuth2ClientAutoConfiguration.class,
        ReactiveOAuth2ResourceServerAutoConfiguration.class,
        ReactiveWebSecurityAutoConfiguration.class
})
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
                "by.egrius.payment_service.controller",
                "by.egrius.payment_service.exception.handler",
                "by.egrius.payment_service.argument_resolver"
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "by\\.egrius\\.payment_service\\.config\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "by\\.egrius\\.payment_service\\.controller\\.oauth\\..*"
                )
        }
)
@EntityScan(basePackages = "by.egrius.payment_service.entity")
@EnableJpaRepositories(basePackages = "by.egrius.payment_service.repository")
@EnableTransactionManagement
public class WebIntegrationTestContext {
}
