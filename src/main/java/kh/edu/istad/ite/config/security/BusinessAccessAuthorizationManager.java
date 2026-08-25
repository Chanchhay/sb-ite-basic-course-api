package kh.edu.istad.ite.config.security;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;

/**
 * Binds a request to the business named in its path.
 *
 * A permission such as {@code item:read} is global to the user: it says the
 * holder may read items, not <em>whose</em> items. Without this, anyone
 * carrying the permission could read any business by putting a different id in
 * the URL, because the id arrives as a path variable and nothing before the
 * controller questions it.
 *
 * The binding already exists in the database — {@code StaffManagementService}
 * writes {@code UserProfile.business} for every staff member it provisions, and
 * {@code Business.keycloakUserId} names the owner. This only consults it, for
 * every request, in one place: a matcher declaring {@code {businessId}} cannot
 * forget the check the way a controller can.
 *
 * Deliberately paired with the permission check rather than replacing it. Being
 * a member of a business is not authority to do everything inside it.
 */
@Component
@RequiredArgsConstructor
public class BusinessAccessAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    /** The matcher must declare this variable, e.g. `/businesses/{businessId}/items`. */
    public static final String BUSINESS_ID = "businessId";

    private static final AuthorizationDecision DENY = new AuthorizationDecision(false);

    private final BusinessRepository businessRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {

        UUID businessId = parseUuid(context.getVariables().get(BUSINESS_ID));
        if (businessId == null) {
            // Either the matcher omitted the variable, or the caller sent
            // something that is not an id. Neither can be authorized; a
            // malformed id must not fall through to the controller.
            return DENY;
        }

        UUID userId = callerId(authentication.get());
        if (userId == null) {
            return DENY;
        }

        if (businessRepository.existsByIdAndKeycloakUserId(businessId, userId)) {
            return new AuthorizationDecision(true);
        }

        // Staff, and only while their membership is active — a suspended
        // account keeps its role in Keycloak until the token expires.
        return new AuthorizationDecision(
                userProfileRepository.existsByUserIdAndBusinessIdAndStaffStatus(
                        userId, businessId, RecordStatus.ACTIVE));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The Keycloak user id, or null when the request is not a signed-in user. */
    private static UUID callerId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication instanceof JwtAuthenticationToken jwt)) {
            return null;
        }
        return parseUuid(jwt.getToken().getSubject());
    }
}
