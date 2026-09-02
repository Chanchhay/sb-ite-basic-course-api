package kh.edu.istad.ite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
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
public class RedisCacheConfig implements CachingConfigurer {

    @Bean
    RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        RedisCacheConfiguration defaults = cacheConfiguration(Duration.ofMinutes(10));

        return RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.ofEntries(
                        Map.entry(CacheNames.CATALOG_ITEMS, cacheConfiguration(Duration.ofMinutes(2))),
                        Map.entry(CacheNames.CATALOG_ITEM_BY_ID, cacheConfiguration(Duration.ofMinutes(5))),
                        Map.entry(CacheNames.CATALOG_ITEM_BY_BARCODE, cacheConfiguration(Duration.ofMinutes(5))),
                        Map.entry(CacheNames.CATALOG_ITEM_GROUPS, cacheConfiguration(Duration.ofMinutes(10))),
                        Map.entry(CacheNames.PUBLIC_STORE_ITEMS, cacheConfiguration(Duration.ofSeconds(30))),
                        Map.entry(CacheNames.PUBLIC_STORE_ITEM_GROUPS, cacheConfiguration(Duration.ofMinutes(5))),
                        Map.entry(CacheNames.PUBLIC_STORE_DETAIL, cacheConfiguration(Duration.ofMinutes(5))),
                        Map.entry(CacheNames.PUBLIC_STORE_FACEBOOK, cacheConfiguration(Duration.ofMinutes(5))),

                        // The directory caches are not evicted by a shop editing
                        // itself, only by one appearing, leaving or moving, so their
                        // TTL is the backstop for everything else that could make a
                        // listing stale. A minute is short enough that nobody notices
                        // and long enough to absorb a shopper paging through results.
                        Map.entry(CacheNames.PUBLIC_STORE_LIST, cacheConfiguration(Duration.ofMinutes(1))),
                        Map.entry(CacheNames.PUBLIC_STORE_RECOMMENDED, cacheConfiguration(Duration.ofMinutes(1))),
                        Map.entry(CacheNames.PUBLIC_STORE_PROVINCES, cacheConfiguration(Duration.ofHours(1)))
                ))
                .transactionAware()
                .build();
    }

    private RedisCacheConfiguration cacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(CacheNames::keyPrefix)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        redisValueSerializer()));
    }

    private GenericJacksonJsonRedisSerializer redisValueSerializer() {
        return GenericJacksonJsonRedisSerializer.create(builder ->
                builder
                        .typePropertyName("@class")
                        .enableDefaultTyping(cacheTypeValidator())
                        // Default typing only tags the root value when Jackson sees a non-final
                        // declared type, and the mapper's default writer infers that type from the
                        // value itself. A `Stream.toList()` result is a final ImmutableCollections
                        // class, so the entry would be written as a bare `[...]` with no `@class`
                        // and then fail to read back as Object. Writing through Object.class keeps
                        // the type hint on every value, whatever its runtime class.
                        .writer((mapper, source) -> mapper.writerFor(Object.class).writeValueAsBytes(source)));
    }

    /**
     * Declared through {@link CachingConfigurer} on purpose: Spring only picks the handler up from
     * a configurer, so a bare {@code CacheErrorHandler} bean would leave the failing default in
     * place and turn every Redis hiccup into a 500.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                try {
                    cache.evict(key);
                } catch (RuntimeException ignored) {
                    // The request can still continue from the database.
                }
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                // Do not fail API requests because Redis cannot store a cache value.
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // TTLs are short for volatile cache entries; stale values age out.
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                // Keep the write operation successful even if Redis is temporarily unavailable.
            }
        };
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
