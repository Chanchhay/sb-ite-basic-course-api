package kh.edu.istad.ite.config.security;

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

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.RecordStatus;

/**
 * The validator answers "whose business", not "may they". It used to answer
 * owner-only, which made every `businessStaffAssignable` role permission
 * unreachable for the staff they were meant for.
 */
class BusinessSecurityValidatorTest {

    private static final UUID BUSINESS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CALLER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final BusinessSecurityValidator validator =
            new BusinessSecurityValidator(businesses, profiles);

    @BeforeEach
    void signIn() {
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

    @Test
    void acceptsTheOwner() {
        givenBusinessOwnedBy(CALLER);

        assertThatCode(() -> validator.validateBusinessAccess(BUSINESS))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsActiveStaff() {
        givenBusinessOwnedBy(OWNER);
        when(profiles.existsByUserIdAndBusinessIdAndStaffStatus(
                CALLER, BUSINESS, RecordStatus.ACTIVE)).thenReturn(true);

        assertThatCode(() -> validator.validateBusinessAccess(BUSINESS))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesSomeoneFromOutsideTheBusiness() {
        givenBusinessOwnedBy(OWNER);
        when(profiles.existsByUserIdAndBusinessIdAndStaffStatus(any(), any(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> validator.validateBusinessAccess(BUSINESS))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void refusesSuspendedStaff() {
        givenBusinessOwnedBy(OWNER);
        // Only ACTIVE membership is looked up, so a suspended member falls
        // through to the refusal even while their Keycloak role lives on.
        when(profiles.existsByUserIdAndBusinessIdAndStaffStatus(
                CALLER, BUSINESS, RecordStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> validator.validateBusinessAccess(BUSINESS))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void reportsAnUnknownBusinessAsNotFound() {
        when(businesses.findById(BUSINESS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateBusinessAccess(BUSINESS))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void theOldNameStillWorksForTheControllersThatCallIt() {
        givenBusinessOwnedBy(CALLER);

        assertThatCode(() -> validator.validateBusinessOwner(BUSINESS))
                .doesNotThrowAnyException();
    }

    private void givenBusinessOwnedBy(UUID ownerId) {
        Business business = new Business();
        business.setKeycloakUserId(ownerId);
        when(businesses.findById(BUSINESS)).thenReturn(Optional.of(business));
    }
}
