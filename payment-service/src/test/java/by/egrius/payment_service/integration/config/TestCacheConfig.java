package by.egrius.payment_service.integration.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;


@Profile("test")
@TestConfiguration
public class TestCacheConfig {

    private static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        redisContainer.start();
    }

    @Bean
    public RedisConnectionFactory lettuceConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redisContainer.getHost(),
                redisContainer.getFirstMappedPort()
        );
        factory.setPassword(null);
        return factory;
    }

    private ObjectMapper objectMapper() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("by.egrius.api_gateway.dto.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.math.")      // Добавьте это для BigDecimal
                .allowIfSubType("java.lang.")      // Добавьте это для String, Integer и т.д.
                .allowIfSubType("java.time.")      // Добавьте это для LocalDateTime и т.д.
                .build();

        return JsonMapper.builder()
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL_AND_RECORDS)
                .build();
    }

    @Bean
    RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        JacksonJsonRedisSerializer<Object> jsonSerializer =
                new JacksonJsonRedisSerializer<>(objectMapper(), Object.class);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        JacksonJsonRedisSerializer<Object> jsonSerializer =
                new JacksonJsonRedisSerializer<>(objectMapper(), Object.class);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                jsonSerializer
                        )
                )
                .disableCachingNullValues();;

        RedisCacheConfiguration accountsCache = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                jsonSerializer
                        )
                );


        RedisCacheConfiguration transfersCache = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                jsonSerializer
                        )
                );

        return RedisCacheManager.builder()
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory))
                .withInitialCacheConfigurations(Map.of(
                        "accounts", accountsCache,
                        "transfers", transfersCache)
                )
                .cacheDefaults(defaultConfig)
                .build();

    }
}
