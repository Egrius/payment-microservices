package by.egrius.payment_service.context;

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
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@Configuration
@EnableAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,

        OAuth2ClientAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class,

        ReactiveOAuth2ClientAutoConfiguration.class,
        ReactiveOAuth2ResourceServerAutoConfiguration.class,
        ReactiveWebSecurityAutoConfiguration.class,

        WebMvcAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class
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
public class ServiceIntegrationTestContext {

}