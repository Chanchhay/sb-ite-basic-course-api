package kh.edu.istad.ite.shared.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Makes a write endpoint safe to send twice.
 * <p>
 * A shopper double-tapping "Place order", or a phone retrying the POST after the
 * reply was lost on a bad connection, otherwise gets two orders and — for KHQR —
 * two payments to reconcile. The client sends the same {@code Idempotency-Key}
 * for every attempt at one logical action; the first attempt runs and its response
 * is remembered, and later attempts carrying that key are answered from the
 * remembered response instead of running again.
 * <p>
 * Only successful responses are remembered. A checkout rejected for an empty cart
 * or missing stock releases the key, because retrying that request after fixing
 * the cart has to be allowed to do real work.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_NAMESPACE = "ite-sb-api::idempotency::";

    /** Marks a key as claimed while the first attempt is still running. Never valid JSON. */
    private static final String IN_PROGRESS = "@in-progress";

    /**
     * How long a claim survives without a stored response. It only has to outlast
     * the slowest legitimate attempt — checkout talks to Bakong — after which a
     * crashed attempt's key must free itself so the shopper can try again.
     */
    private static final Duration CLAIM_TTL = Duration.ofSeconds(60);

    /**
     * How long a completed response stays replayable. Long enough to cover a
     * shopper retrying over a flaky connection, short enough that tomorrow's
     * checkout under a recycled key is not answered with yesterday's order.
     */
    private static final Duration RESPONSE_TTL = Duration.ofHours(24);

    /**
     * Runs {@code action} once per {@code (scope, owner, key)}, replaying the first
     * attempt's response for any repeat.
     *
     * @param scope the operation being guarded, so one endpoint's keys cannot
     *              collide with another's
     * @param owner who the key belongs to; a key from one shopper must never
     *              replay another's response, whatever value they send
     * @param key   the client's idempotency key. Blank means no guarantee was
     *              asked for and the action simply runs, which is what keeps this
     *              safe to add to an endpoint whose callers do not send one yet.
     * @param type  the response type, for reading a remembered response back
     */
    public <T> T execute(String scope, String owner, String key, Class<T> type, Supplier<T> action) {
        if (!StringUtils.hasText(key)) {
            return action.get();
        }

        String redisKey = KEY_NAMESPACE + scope + "::" + owner + "::" + key.trim();

        Boolean claimed;
        try {
            claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, CLAIM_TTL);
        } catch (RuntimeException ex) {
            // Redis is a safety net here, not the system of record. Losing it must
            // not stop shoppers checking out; it only means a duplicate submitted
            // during the outage goes uncaught.
            log.warn("Idempotency unavailable for {}; proceeding without replay protection", scope, ex);
            return action.get();
        }

        if (!Boolean.TRUE.equals(claimed)) {
            return replay(redisKey, type);
        }

        T response;
        try {
            response = action.get();
        } catch (RuntimeException ex) {
            release(redisKey);
            throw ex;
        }

        remember(redisKey, response);
        return response;
    }

    private <T> T replay(String redisKey, Class<T> type) {
        String stored = redisTemplate.opsForValue().get(redisKey);

        if (stored == null) {
            // Claimed a moment ago, gone now: the first attempt failed and released
            // it. Nothing was created, so the caller is free to try again.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "That request could not be completed. Please try again.");
        }

        if (IN_PROGRESS.equals(stored)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This request is already being processed.");
        }

        try {
            return objectMapper.readValue(stored, type);
        } catch (RuntimeException ex) {
            // A remembered response we can no longer read is worse than none:
            // answering with an error is safe, running the action again is not.
            log.error("Could not read remembered response for {}", redisKey, ex);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This request has already been processed.");
        }
    }

    private void remember(String redisKey, Object response) {
        try {
            redisTemplate.opsForValue().set(
                    redisKey, objectMapper.writeValueAsString(response), RESPONSE_TTL);
        } catch (RuntimeException ex) {
            // The action already succeeded and must stand. Release the claim rather
            // than leave it blocking retries for a response nobody can replay.
            log.warn("Could not remember response for {}", redisKey, ex);
            release(redisKey);
        }
    }

    private void release(String redisKey) {
        try {
            redisTemplate.delete(redisKey);
        } catch (RuntimeException ex) {
            log.warn("Could not release idempotency key {}; it expires in {}", redisKey, CLAIM_TTL, ex);
        }
    }
}
