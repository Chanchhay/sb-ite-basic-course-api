package kh.edu.istad.ite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    @Bean
    RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        RedisCacheConfiguration defaults = cacheConfiguration(Duration.ofMinutes(10));

        return RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.CATALOG_ITEMS, cacheConfiguration(Duration.ofMinutes(2)),
                        CacheNames.CATALOG_ITEM_BY_ID, cacheConfiguration(Duration.ofMinutes(5)),
                        CacheNames.CATALOG_ITEM_BY_BARCODE, cacheConfiguration(Duration.ofMinutes(5)),
                        CacheNames.CATALOG_ITEM_GROUPS, cacheConfiguration(Duration.ofMinutes(10)),
                        CacheNames.PUBLIC_STORE_ITEMS, cacheConfiguration(Duration.ofSeconds(30)),
                        CacheNames.PUBLIC_STORE_ITEM_GROUPS, cacheConfiguration(Duration.ofMinutes(5))
                ))
                .transactionAware()
                .build();
    }

    private RedisCacheConfiguration cacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "ite-sb-api::" + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        redisValueSerializer()));
    }

    private GenericJacksonJsonRedisSerializer redisValueSerializer() {
        return GenericJacksonJsonRedisSerializer.create(builder ->
                builder.enableDefaultTyping(cacheTypeValidator()));
    }

    private PolymorphicTypeValidator cacheTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("kh.edu.istad.ite.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.net.")
                .allowIfSubType("org.springframework.data.domain.")
                .build();
    }
}
