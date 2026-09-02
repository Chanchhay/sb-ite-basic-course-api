package kh.edu.istad.ite.shared.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Counts what one caller has done inside a window, in Redis so that every API
 * instance counts against the same total.
 * <p>
 * The window is fixed rather than sliding: a counter per {@code (rule, caller,
 * window)} that expires on its own. A caller can therefore burst across a window
 * boundary — up to twice the limit in one unlucky minute — which is the accepted
 * cost of an algorithm that is one round trip, holds no state between requests,
 * and cannot itself become the bottleneck it exists to prevent.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_NAMESPACE = "ite-sb-api::rate-limit::";

    /**
     * Counting and setting the expiry have to happen together. As two round trips,
     * a process dying in between leaves a counter with no expiry, which locks that
     * caller out of that endpoint until someone notices and deletes the key.
     */
    private static final RedisScript<List> COUNT_IN_WINDOW = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return { count, redis.call('PTTL', KEYS[1]) }
            """, List.class);

    /**
     * Records one request and says whether it is within the limit.
     *
     * @param rule   which limit is being applied, so two endpoints never share a counter
     * @param caller who is being counted, normally their address
     */
    public Decision check(String rule, String caller, int limit, Duration window) {
        String key = KEY_NAMESPACE + rule + "::" + caller;

        try {
            List<?> result = redisTemplate.execute(
                    COUNT_IN_WINDOW, List.of(key), String.valueOf(window.toMillis()));

            if (result == null || result.size() < 2) {
                return Decision.allow();
            }

            long count = ((Number) result.get(0)).longValue();
            long millisLeft = ((Number) result.get(1)).longValue();

            if (count <= limit) {
                return Decision.allow();
            }

            // PTTL answers -1 for a key with no expiry and -2 for one that has just
            // vanished; neither is a sane wait, so fall back to the whole window.
            long retryAfterSeconds = millisLeft > 0
                    ? Math.max(1, (millisLeft + 999) / 1000)
                    : Math.max(1, window.toSeconds());

            return Decision.deny(retryAfterSeconds);
        } catch (RuntimeException ex) {
            // A rate limiter that fails closed turns a Redis outage into a total
            // outage. Letting requests through unmetered is the lesser harm, and
            // the endpoints behind it are not made unsafe by the absence of a
            // limit — only more expensive.
            log.warn("Rate limit check failed for rule {}; allowing the request", rule, ex);
            return Decision.allow();
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {

        static Decision allow() {
            return new Decision(true, 0);
        }

        static Decision deny(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
