package kh.edu.istad.ite.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;

/**
 * How many requests one caller may make to an endpoint before being asked to slow
 * down.
 * <p>
 * The defaults below are the shipped policy; {@code app.rate-limit.rules} in the
 * profile replaces them wholesale, so a limit can be retuned without a release.
 * Callers are counted by address, and on mobile networks one address is shared by
 * a great many people, so every limit here has to hold for a whole carrier's worth
 * of shoppers at once rather than for one phone. They are therefore set well above
 * any real session and still far below what a script does: the cost of turning
 * away a genuine customer is much higher than the cost of letting an abusive
 * caller through for another few seconds.
 */
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProps {

    /** The escape hatch: turns every rule off without a redeploy. */
    private boolean enabled = true;

    /**
     * Checked in order, first match wins, so put the narrow rules before the broad
     * ones. Anything matching no rule is not limited.
     */
    private List<Rule> rules = List.of(
            // Creating an order is the expensive, stateful one, and the tightest
            // limit for that reason. Thirty a minute is more orders than one shared
            // mobile address plausibly places and a small fraction of what a script
            // placing them in a loop would.
            rule("checkout", List.of(HttpMethod.POST),
                    List.of("/api/v1/storefront/checkout"), 30, Duration.ofMinutes(1)),

            // Sign-in and registration, where an open endpoint invites credential
            // stuffing and throwaway-account creation.
            rule("auth", List.of(HttpMethod.POST),
                    List.of("/api/v1/auth/register/**", "/api/v1/auth/register",
                            "/api/v1/telegram-webapp/auth", "/api/v1/facebook-webapp/auth",
                            "/api/v1/facebook-webapp/device-auth"),
                    30, Duration.ofMinutes(1)),

            // The unauthenticated marketplace. Generous, because one page view is
            // already several requests and the storefront fans out one more per
            // category filter — this is here to stop scraping and cache-busting
            // floods, not to police browsing.
            rule("public-read", List.of(HttpMethod.GET),
                    List.of("/api/v1/public/**"), 600, Duration.ofMinutes(1))
    );

    private static Rule rule(
            String name, List<HttpMethod> methods, List<String> paths, int limit, Duration window) {
        Rule rule = new Rule();
        rule.setName(name);
        rule.setMethods(methods);
        rule.setPaths(paths);
        rule.setLimit(limit);
        rule.setWindow(window);
        return rule;
    }

    @Getter
    @Setter
    public static class Rule {

        /** Names the counter in Redis and the log line, so a limit that fires is identifiable. */
        private String name;

        /** Empty means every method. */
        private List<HttpMethod> methods = List.of();

        /** Ant-style patterns, matched against the request path. */
        private List<String> paths = List.of();

        /** Requests allowed per window, per caller. */
        private int limit;

        private Duration window = Duration.ofMinutes(1);
    }
}
