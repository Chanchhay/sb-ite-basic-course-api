package kh.edu.istad.ite.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.RequestMatcher;

import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.RecordStatus;

/**
 * The manager is only as good as the path variable it reads, so these cover
 * both halves: that `{businessId}` survives the matcher, and that a stranger
 * carrying the right permission is still refused.
 */
class BusinessAccessAuthorizationManagerTest {

    private static final UUID BUSINESS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_BUSINESS = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CALLER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final BusinessAccessAuthorizationManager manager =
            new BusinessAccessAuthorizationManager(businesses, profiles);

    /* ---- the assumption the whole design rests on ---- */

    @Test
    void matcherCapturesBusinessIdFromThePath() {
        RequestMatcher.MatchResult result = pathPattern(
                HttpMethod.GET, "/api/v1/businesses/{businessId}/items")
                .matcher(get("/api/v1/businesses/" + BUSINESS + "/items"));

        assertThat(result.isMatch()).isTrue();
        assertThat(result.getVariables()).containsEntry("businessId", BUSINESS.toString());
    }

    @Test
    void catchAllMatcherAlsoCapturesBusinessId() {
        RequestMatcher.MatchResult result = pathPattern("/api/v1/businesses/{businessId}/**")
                .matcher(get("/api/v1/businesses/" + BUSINESS + "/discounts/abc"));

        assertThat(result.isMatch()).isTrue();
        assertThat(result.getVariables()).containsEntry("businessId", BUSINESS.toString());
    }

    @Test
    void catchAllAlsoMatchesTheSettingsPaths_soOrderingIsLoadBearing() {
        // `{businessId}` is just a segment: it happily captures "storefront",
        // and `/**` matches nothing after it. So the catch-all DOES match these
        // paths, and would deny them — "storefront" is not a UUID. They stay
        // reachable only because SecurityConfig lists their own rules first and
        // the first match wins. Never move the catch-all up.
        assertThat(pathPattern("/api/v1/businesses/{businessId}/**")
                .matcher(get("/api/v1/businesses/storefront")).getVariables())
                .containsEntry("businessId", "storefront");

        // `/businesses/me` too. It is the call the dashboard opens on, and the
        // whole app refuses to load when it fails, so if the catch-all ever
        // reaches it first every account is locked out at the login screen.
        assertThat(pathPattern("/api/v1/businesses/{businessId}/**")
                .matcher(get("/api/v1/businesses/me")).getVariables())
                .containsEntry("businessId", "me");

        assertThat(manager.authorize(this::caller, new RequestAuthorizationContext(
                get("/api/v1/businesses/storefront"), Map.of("businessId", "storefront")))
                .isGranted()).isFalse();
    }

    /* ---- the decision ---- */

    @Test
    void grantsTheOwner() {
        when(businesses.existsByIdAndKeycloakUserId(BUSINESS, CALLER)).thenReturn(true);

        assertThat(authorize(BUSINESS.toString()).isGranted()).isTrue();
    }

    @Test
    void grantsActiveStaffOfThatBusiness() {
        when(businesses.existsByIdAndKeycloakUserId(BUSINESS, CALLER)).thenReturn(false);
        when(profiles.existsByUserIdAndBusinessIdAndStaffStatus(
                CALLER, BUSINESS, RecordStatus.ACTIVE)).thenReturn(true);

        assertThat(authorize(BUSINESS.toString()).isGranted()).isTrue();
    }

    @Test
    void refusesSomeoneWhoBelongsToAnotherBusiness() {
        // The caller is staff at BUSINESS, and asks for OTHER_BUSINESS. This is
        // the cross-tenant read the permission alone could not prevent.
        when(businesses.existsByIdAndKeycloakUserId(any(), any())).thenReturn(false);
        when(profiles.existsByUserIdAndBusinessIdAndStaffStatus(any(), any(), any()))
                .thenReturn(false);

        assertThat(authorize(OTHER_BUSINESS.toString()).isGranted()).isFalse();
    }

    @Test
    void refusesWhenTheMatcherDeclaredNoBusinessId() {
        RequestAuthorizationContext context = new RequestAuthorizationContext(
                get("/api/v1/businesses/storefront"), Map.of());

        assertThat(manager.authorize(this::caller, context).isGranted()).isFalse();
    }

    @Test
    void refusesAMalformedBusinessId() {
        assertThat(authorize("not-a-uuid").isGranted()).isFalse();
    }

    /* ---- helpers ---- */

    private org.springframework.security.authorization.AuthorizationResult authorize(String businessId) {
        RequestAuthorizationContext context = new RequestAuthorizationContext(
                get("/api/v1/businesses/" + businessId + "/items"),
                Map.of("businessId", businessId));

        return manager.authorize(this::caller, context);
    }

    private Authentication caller() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(CALLER.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        return new JwtAuthenticationToken(jwt, java.util.List.of());
    }

    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        return request;
    }
}
