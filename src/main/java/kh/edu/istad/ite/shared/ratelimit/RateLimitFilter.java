package kh.edu.istad.ite.shared.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kh.edu.istad.ite.config.props.RateLimitProps;
import kh.edu.istad.ite.shared.exception.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * Turns away a caller who is asking for far more than any real session would.
 * <p>
 * Sits inside the security chain, after CORS and before authentication: after CORS
 * so a browser can actually read the 429 instead of reporting an opaque network
 * error, and before authentication so an unauthenticated flood is rejected without
 * ever reaching the database or a token check.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProps props;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!props.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitProps.Rule rule = firstMatching(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        RateLimiter.Decision decision =
                rateLimiter.check(rule.getName(), callerOf(request), rule.getLimit(), rule.getWindow());

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        log.info("Rate limit '{}' reached by {} on {} {}",
                rule.getName(), callerOf(request), request.getMethod(), request.getRequestURI());
        reject(response, decision.retryAfterSeconds());
    }

    private RateLimitProps.Rule firstMatching(HttpServletRequest request) {
        String path = request.getRequestURI();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        for (RateLimitProps.Rule rule : props.getRules()) {
            boolean methodMatches = rule.getMethods().isEmpty() || rule.getMethods().contains(method);
            if (methodMatches && rule.getPaths().stream().anyMatch(p -> pathMatcher.match(p, path))) {
                return rule;
            }
        }

        return null;
    }

    /**
     * Who to count this request against.
     * <p>
     * Every request arrives through Traefik, which appends the address it saw to
     * {@code X-Forwarded-For}, so the last entry is the real caller. The earlier
     * entries are whatever the caller chose to send and must never be trusted —
     * reading the first entry, which is what {@code getRemoteAddr()} reports once
     * Spring's forwarded-header handling is active, would let anyone dodge every
     * limit here by inventing a new address per request.
     */
    private String callerOf(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (StringUtils.hasText(forwardedFor)) {
            String[] hops = forwardedFor.split(",");
            String nearest = hops[hops.length - 1].trim();
            if (StringUtils.hasText(nearest)) {
                return nearest;
            }
        }

        // No proxy in front, so the connection itself is the truth.
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        // A filter runs outside @ControllerAdvice, so the body is written here to
        // match what every other error on this API looks like.
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.toString())
                .code(HttpStatus.TOO_MANY_REQUESTS.value())
                .message("Too many requests. Please try again in " + retryAfterSeconds + " seconds.")
                .timestamp(Instant.now())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
