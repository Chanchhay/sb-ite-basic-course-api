package kh.edu.istad.ite.shared.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import kh.edu.istad.ite.features.admin.repository.PlatformFeatureFlagRepository;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessFeatureFlagRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.RecordStatus;

/**
 * Staff own no business. Every resolver here used to ask about ownership, which
 * is why a staff member with permissions could not load a single screen.
 */
class BusinessHelperTest {

    private static final UUID BUSINESS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CALLER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final BusinessHelper helper = new BusinessHelper(
            businesses,
            profiles,
            mock(BusinessFeatureFlagRepository.class),
            mock(PlatformFeatureFlagRepository.class));

    private final Business business = new Business();

    @BeforeEach
    void signIn() {
        business.setId(BUSINESS_ID);
        business.setKeycloakUserId(OWNER);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(CALLER.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /* ---- currentBusiness: what GET /businesses/me answers ---- */

    @Test
    void currentBusinessFindsTheOwnersOwnBusiness() {
        when(businesses.findByKeycloakUserId(CALLER)).thenReturn(Optional.of(business));

        assertThat(helper.currentBusiness().getId()).isEqualTo(BUSINESS_ID);
    }

    @Test
    void currentBusinessFindsTheBusinessAStaffMemberWorksIn() {
        // The regression that made a staff account useless: they own nothing,
        // so this returned empty and every screen 404'd.
        when(businesses.findByKeycloakUserId(CALLER)).thenReturn(Optional.empty());
        when(profiles.findFirstByUserIdAndStaffStatusAndBusinessIsNotNull(
                CALLER, RecordStatus.ACTIVE)).thenReturn(Optional.of(profileFor(business)));

        assertThat(helper.currentBusiness().getId()).isEqualTo(BUSINESS_ID);
    }

    @Test
    void currentBusinessStillReportsNotFoundForSomeoneWithNoBusinessAtAll() {
        when(businesses.findByKeycloakUserId(CALLER)).thenReturn(Optional.empty());
        when(profiles.findFirstByUserIdAndStaffStatusAndBusinessIsNotNull(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(helper::currentBusiness)
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void currentBusinessOrEmptyAnswersEmptyRatherThanThrowing() {
        when(businesses.findByKeycloakUserId(CALLER)).thenReturn(Optional.empty());
        when(profiles.findFirstByUserIdAndStaffStatusAndBusinessIsNotNull(any(), any()))
                .thenReturn(Optional.empty());

        assertThat(helper.currentBusinessOrEmpty()).isEmpty();
    }

    /* ---- findOwnedBusiness: now membership, not ownership ---- */

    @Test
    @SuppressWarnings("deprecation")
    void findOwnedBusinessAcceptsActiveStaff() {
        when(businesses.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
        when(profiles.existsByUserIdAndBusinessIdAndStaffStatus(
                CALLER, BUSINESS_ID, RecordStatus.ACTIVE)).thenReturn(true);

        assertThatCode(() -> helper.findOwnedBusiness(BUSINESS_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("deprecation")
    void findOwnedBusinessStillRefusesAnOutsider() {
        when(businesses.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
        when(profiles.existsByUserIdAndBusinessIdAndStaffStatus(any(), any(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> helper.findOwnedBusiness(BUSINESS_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    private static UserProfile profileFor(Business business) {
        UserProfile profile = new UserProfile();
        profile.setUserId(CALLER);
        profile.setBusiness(business);
        profile.setStaffStatus(RecordStatus.ACTIVE);
        return profile;
    }
}
