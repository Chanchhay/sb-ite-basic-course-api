package kh.edu.istad.ite.features.audit.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kh.edu.istad.ite.features.audit.service.BusinessAuditService;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns "this request is authenticated" into "this person signed in".
 *
 * There is no sign-in endpoint to hook: authentication happens at Keycloak and
 * the application only ever meets the resulting token. What the token does
 * carry is `sid`, the Keycloak session — constant for the life of one sign-in
 * and different for the next — so the first request bearing an unseen `sid`
 * *is* the sign-in, as far as anything on this side can observe.
 *
 * What that means for the log, and it is worth being straight about: this
 * records sessions that used the application, not authentications. Someone who
 * signs in and closes the tab before anything loads leaves no row, and a failed
 * password attempt never reaches here at all — Keycloak turns it away long
 * before a token exists. For "who has been in my shop's account", which is the
 * question this answers, that is the right set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignInAuditFilter extends OncePerRequestFilter {

    /**
     * How many sessions to remember before forgetting the oldest.
     *
     * The unique constraint on the table is what actually prevents duplicates.
     * This exists only so the common case — the second and every later request
     * of a session — costs a map lookup instead of a database round trip.
     * Overflowing it is harmless: the worst outcome is one more `exists` query.
     */
    private static final int REMEMBERED_SESSIONS = 20_000;

    private final BusinessAuditService auditService;
    private final BusinessHelper businessHelper;

    private final Map<String, Boolean> recordedSessions = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            recordIfNewSession();
        } catch (Exception e) {
            // Nothing here is worth failing a request over.
            log.warn("Could not record sign-in", e);
        }

        filterChain.doFilter(request, response);
    }

    private void recordIfNewSession() {
        String sessionId = currentSessionId();

        if (sessionId == null || recordedSessions.containsKey(sessionId)) {
            return;
        }

        // Claimed before the work, not after: two requests arriving together on
        // a fresh session would otherwise both look new. The loser of that race
        // still hits the unique constraint, which is the real guard.
        if (recordedSessions.putIfAbsent(sessionId, Boolean.TRUE) != null) {
            return;
        }

        if (recordedSessions.size() > REMEMBERED_SESSIONS) {
            recordedSessions.clear();
        }

        // A platform account belongs to no shop and has no shop log to appear
        // in. Resolved once per session, which is why it can afford to be a
        // database lookup at all.
        Optional<Business> business = businessHelper.currentBusinessOrEmpty();

        business.ifPresent(value -> auditService.recordSignIn(value.getId(), sessionId));
    }

    /** The Keycloak session the caller's token belongs to. */
    private String currentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            return null;
        }

        Jwt jwt = token.getToken();
        String sessionId = jwt.getClaimAsString("sid");

        // Older Keycloak clients, and service accounts, issue tokens with no
        // `sid`. The token id is per-token rather than per-session, so it would
        // log a row on every refresh; better to record nothing than to invent
        // a sign-in every five minutes.
        return StringUtils.hasText(sessionId) ? sessionId : null;
    }
}
