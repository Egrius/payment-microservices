    package by.egrius.payment_service.integration.config;

    import by.egrius.payment_service.event.TransferAddedEvent;
    import by.egrius.payment_service.event.TransferProcessedEvent;
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
    import org.springframework.beans.factory.annotation.Qualifier;
    import org.springframework.boot.test.context.TestConfiguration;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Primary;
    import org.springframework.dao.OptimisticLockingFailureException;

    import java.util.HashMap;
    import java.util.Map;

    @TestConfiguration
    @EnableRabbit
    public class TestRabbitMQConfig {

        @Bean
        public Queue interceptorQueue() {
            return QueueBuilder
                    .durable("interceptor.queue")
                    .build();
        }

        @Bean
        public Binding interceptorBinding() {
            return BindingBuilder
                    .bind(interceptorQueue())
                    .to(processingTopicExchange())
                    .with("transfer.processing");
        }

        // === CONNECTION ===
        @Bean
        @Primary
        public ConnectionFactory connectionFactory() {
            CachingConnectionFactory factory = new CachingConnectionFactory("localhost", 5672);
            factory.setUsername("guest");
            factory.setPassword("guest");
            factory.setVirtualHost("/test");
            return factory;
        }

        // === QUEUES ===
        @Bean
        public Queue processingQueue() {
            return QueueBuilder
                    .durable("processing.queue")
                    .build();
        }

        @Bean
        public Queue notificationQueue() {
            return QueueBuilder
                    .durable("notification.queue")
                    .build();
        }

        // === EXCHANGES ===
        @Bean
        public TopicExchange processingTopicExchange() {
            return ExchangeBuilder
                    .topicExchange("processing.exchange")
                    .durable(true)
                    .build();
        }

        @Bean
        public TopicExchange paymentTopicExchange() {
            return ExchangeBuilder
                    .topicExchange("payment.exchange")
                    .durable(true)
                    .build();
        }

        // === BINDINGS ===
        @Bean
        public Binding bindProcessingQueue() {
            return BindingBuilder
                    .bind(processingQueue())
                    .to(processingTopicExchange())
                    .with("transfer.processing");
        }

        @Bean
        public Binding bindNotificationQueue() {
            return BindingBuilder
                    .bind(notificationQueue())
                    .to(paymentTopicExchange())
                    .with("transfer.completed");
        }

        // === MESSAGE CONVERTER ===
        @Bean
        @Primary
        public MessageConverter messageConverter() {
            JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

            converter.setClassMapper(classMapper());
            return converter;
        }

        @Bean
        public DefaultClassMapper classMapper() {
            DefaultClassMapper classMapper = new DefaultClassMapper();
            classMapper.setTrustedPackages(
                    "by.egrius.payment_service.event",
                    "by.egrius.payment_service.dto"
            );
            Map<String, Class<?>> idClassMapping = new HashMap<>();
            idClassMapping.put("TransferAddedEvent", TransferAddedEvent.class);
            idClassMapping.put("TransferProcessedEvent", TransferProcessedEvent.class);
            classMapper.setIdClassMapping(idClassMapping);
            return classMapper;
        }

        // === LISTENER CONTAINER FACTORY ===
        @Bean
        @Primary
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

        // === RABBIT TEMPLATE ===
        @Bean
        @Primary
        public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                             MessageConverter messageConverter) {
            RabbitTemplate template = new RabbitTemplate(connectionFactory);
            template.setMessageConverter(messageConverter);
            return template;
        }
    }