package by.egrius.payment_service.integration.authorization.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

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
