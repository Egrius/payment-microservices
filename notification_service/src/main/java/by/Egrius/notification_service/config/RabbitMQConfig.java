package by.Egrius.notification_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public Queue notificationQueue() {
        return QueueBuilder
                .durable("notification.queue")
                .build();
    }

    @Bean
        public TopicExchange paymentTopicExchange() {
            return ExchangeBuilder.topicExchange("payment.exchange")
                    .durable(true)
                    .build();
    }

    @Bean
    public Binding bindNotificationQueue() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(paymentTopicExchange())
                .with("transfer.completed");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
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
                .build());
        return factory;
    }

    @Bean
    public CachingConnectionFactory cachingConnectionFactory() {
        CachingConnectionFactory ccf = new CachingConnectionFactory(host, port);
        ccf.setUsername(username);
        ccf.setPassword(password);

        return ccf;
    }
}