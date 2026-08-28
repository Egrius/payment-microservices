package by.egrius.payment_service.config.prod;

import by.egrius.payment_service.event.TransferAddedEvent;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@EnableRabbit
@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port}")
    private int port;

    @Bean
    public Queue processingQueue() {
        return QueueBuilder
                .durable("processing.queue")
                .build();
    }

    @Bean
    public TopicExchange processingTopicExchange() {
        return ExchangeBuilder.topicExchange("processing.exchange")
                .durable(true)
                .build();
    }

    @Bean
    public Binding bindProcessingQueue() {
        return BindingBuilder
                .bind(processingQueue())
                .to(processingTopicExchange())
                .with("transfer.processing");
    }


    @Bean
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(host, port);

        cachingConnectionFactory.setUsername(username);
        cachingConnectionFactory.setPassword(password);
        return cachingConnectionFactory;
    }

    @Bean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

        converter.setClassMapper(classMapper());
        return converter;
    }

    @Bean
    public DefaultClassMapper classMapper() {
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("by.egrius.payment_service.event");
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put("TransferAddedEvent", TransferAddedEvent.class);
        classMapper.setIdClassMapping(idClassMapping);
        return classMapper;
    }

    @Bean
    public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);

        factory.setAdviceChain(RetryInterceptorBuilder
                .stateless()
                .maxRetries(3)
                .backOffOptions(1000, 2.0, 5000)
                .configureRetryPolicy(c ->
                        c.includes(OptimisticLockingFailureException.class))
                .build());
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate template = new RabbitTemplate(connectionFactory());
        template.setMessageConverter(messageConverter());
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .delay(Duration.ofMillis(500))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(10))
                .build();
        template.setRetryTemplate(new RetryTemplate(retryPolicy));

        return template;
    }
}