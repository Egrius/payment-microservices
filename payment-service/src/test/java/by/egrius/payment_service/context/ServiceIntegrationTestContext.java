package by.egrius.payment_service.context;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.concurrent.Executor;

@Configuration
@EnableAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
})
@ComponentScan(
        basePackages = {
                "by.egrius.api_gateway.service",
                "by.egrius.api_gateway.repository",
                "by.egrius.api_gateway.mapper",
                "by.egrius.api_gateway.event",
                "by.egrius.api_gateway.entity",
                "by.egrius.api_gateway.dto",
                "by.egrius.api_gateway.exception"
        },
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "by\\.egrius\\.api_gateway\\.config\\..*"
                ),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "by\\.egrius\\.api_gateway\\.controller\\..*"
                )
        }
)
@EntityScan(basePackages = "by.egrius.api_gateway.entity")
@EnableJpaRepositories(basePackages = "by.egrius.api_gateway.repository")
@EnableTransactionManagement
@EnableAsync
public class ServiceIntegrationTestContext {

    @Bean(name = "transfer-task-pool")
    public Executor transferTaskPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("transfer-");
        executor.initialize();
        return executor;
    }
}