package kh.edu.istad.ite.features.audit.service.impl;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import kh.edu.istad.ite.features.audit.dto.response.BusinessAuditLogResponse;
import kh.edu.istad.ite.features.audit.entity.BusinessAuditLog;
import kh.edu.istad.ite.features.audit.repository.BusinessAuditLogRepository;
import kh.edu.istad.ite.features.audit.service.BusinessAuditService;
import kh.edu.istad.ite.shared.enums.BusinessAuditAction;
import kh.edu.istad.ite.shared.enums.BusinessAuditTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessAuditServiceImpl implements BusinessAuditService {

    private static final String UNKNOWN_ACTOR = "UNKNOWN";
    private static final int LABEL_MAX_LENGTH = 255;
    private static final int STATE_MAX_LENGTH = 255;
    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final BusinessAuditLogRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID businessId,
            BusinessAuditAction actionType,
            BusinessAuditTarget targetType,
            String targetId,
            String targetLabel,
            String previousState,
            String newState
    ) {
        if (businessId == null) {
            return;
        }

        try {
            save(businessId, actionType, targetType, targetId, targetLabel,
                    previousState, newState, null);
        } catch (Exception e) {
            // The action this describes has already happened. Letting the log
            // fail the request would undo real work — a staff member left
            // un-suspended because their audit row would not write — so this
            // is loud in the logs and silent to the caller.
            log.warn("Could not record {} on business {}", actionType, businessId, e);
        }
    }

    /**
     * A new transaction, so an audit row is not rolled back with the request.
     *
     * The caller's own transaction may still fail after this point for reasons
     * that have nothing to do with the log — and "we changed nothing, and also
     * recorded nothing" is right for the change but wrong for a sign-in, which
     * has already happened whatever the request goes on to do.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSignIn(UUID businessId, String sessionId) {
        if (businessId == null || !StringUtils.hasText(sessionId)) {
            return;
        }

        try {
            if (repository.existsByBusinessIdAndSessionId(businessId, sessionId)) {
                return;
            }

            save(businessId, BusinessAuditAction.STAFF_SIGNED_IN, BusinessAuditTarget.STAFF,
                    currentActorId(), currentActorUsername(), null, null, sessionId);
        } catch (DataIntegrityViolationException e) {
            // Two requests of the same fresh session raced the exists check.
            // The unique constraint is the real guard; this is the expected
            // way to lose that race, not a problem.
            log.debug("Sign-in for session {} already recorded", sessionId);
        } catch (Exception e) {
            log.warn("Could not record sign-in for business {}", businessId, e);
        }
    }

    private void save(
            UUID businessId,
            BusinessAuditAction actionType,
            BusinessAuditTarget targetType,
            String targetId,
            String targetLabel,
            String previousState,
            String newState,
            String sessionId
    ) {
        BusinessAuditLog entry = new BusinessAuditLog();

        entry.setBusinessId(businessId);
        entry.setActorId(currentActorId());
        entry.setActorUsername(currentActorUsername());
        entry.setActionType(actionType);
        entry.setTargetType(targetType);
        entry.setTargetId(truncate(targetId, 100));
        entry.setTargetLabel(truncate(targetLabel, LABEL_MAX_LENGTH));
        entry.setPreviousState(truncate(previousState, STATE_MAX_LENGTH));
        entry.setNewState(truncate(newState, STATE_MAX_LENGTH));
        entry.setSessionId(sessionId);
        entry.setIpAddress(currentIpAddress());
        entry.setUserAgent(truncate(currentUserAgent(), USER_AGENT_MAX_LENGTH));
        entry.setCreatedAt(LocalDateTime.now());

        repository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BusinessAuditLogResponse> getAuditLogs(
            UUID businessId,
            BusinessAuditAction actionType,
            BusinessAuditTarget targetType,
            String actorId,
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            Pageable pageable
    ) {
        return repository
                .findAll(withFilters(businessId, actionType, targetType, actorId, from, to, keyword), pageable)
                .map(this::toResponse);
    }

    /**
     * The business clause is not one of the filters — it is ANDed first and
     * cannot be widened by anything a caller sends.
     */
    private Specification<BusinessAuditLog> withFilters(
            UUID businessId,
            BusinessAuditAction actionType,
            BusinessAuditTarget targetType,
            String actorId,
            LocalDateTime from,
            LocalDateTime to,
            String keyword
    ) {
        return (root, query, cb) -> {
            List<Predicate> clauses = new ArrayList<>();

            clauses.add(cb.equal(root.get("businessId"), businessId));

            if (actionType != null) {
                clauses.add(cb.equal(root.get("actionType"), actionType));
            }
            if (targetType != null) {
                clauses.add(cb.equal(root.get("targetType"), targetType));
            }
            if (StringUtils.hasText(actorId)) {
                clauses.add(cb.equal(root.get("actorId"), actorId));
            }
            if (from != null) {
                clauses.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                clauses.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                clauses.add(cb.or(
                        cb.like(cb.lower(root.get("actorUsername")), like),
                        cb.like(cb.lower(root.get("targetLabel")), like)));
            }

            return cb.and(clauses.toArray(new Predicate[0]));
        };
    }

    private BusinessAuditLogResponse toResponse(BusinessAuditLog entry) {
        return new BusinessAuditLogResponse(
                entry.getId(),
                entry.getActorId(),
                entry.getActorUsername(),
                entry.getActionType(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getTargetLabel(),
                entry.getPreviousState(),
                entry.getNewState(),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.getCreatedAt());
    }

    private String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getSubject();
        }

        return UNKNOWN_ACTOR;
    }

    private String currentActorUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwt) {
            String name = jwt.getToken().getClaimAsString("name");
            if (StringUtils.hasText(name)) {
                return name;
            }

            String username = jwt.getToken().getClaimAsString("preferred_username");
            if (StringUtils.hasText(username)) {
                return username;
            }

            String email = jwt.getToken().getClaimAsString("email");
            if (StringUtils.hasText(email)) {
                return email;
            }
        }

        return UNKNOWN_ACTOR;
    }

    /**
     * Where the person actually was, as far as it can be known.
     *
     * The back office reaches this API through its own Next.js server, so the
     * connection is always from that server and never from the member of
     * staff. It forwards what it saw as {@code X-Client-IP} — the same header
     * the rate limiter prefers, for the same reason — and that is read first.
     *
     * Falling back to the *last* hop of {@code X-Forwarded-For}, not the
     * first: earlier entries are whatever the caller chose to send, so reading
     * the front of the list would record an address anyone could invent.
     */
    private String currentIpAddress() {
        HttpServletRequest request = currentRequest();

        if (request == null) {
            return null;
        }

        String forwardedClient = request.getHeader("X-Client-IP");
        if (StringUtils.hasText(forwardedClient)) {
            return forwardedClient.trim();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String[] hops = forwardedFor.split(",");
            String nearest = hops[hops.length - 1].trim();
            if (StringUtils.hasText(nearest)) {
                return nearest;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * The browser, not the proxy.
     *
     * {@code User-Agent} on a proxied call describes the fetch this server
     * made; the browser's own is forwarded beside it.
     */
    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();

        if (request == null) {
            return null;
        }

        String forwarded = request.getHeader("X-Client-User-Agent");

        return StringUtils.hasText(forwarded) ? forwarded : request.getHeader("User-Agent");
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }

        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
