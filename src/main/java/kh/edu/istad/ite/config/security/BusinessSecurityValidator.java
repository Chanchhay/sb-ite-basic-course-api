package kh.edu.istad.ite.config.security;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BusinessSecurityValidator {

    private final BusinessRepository businessRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * The caller must belong to this business — as its owner, or as active staff.
     *
     * This used to accept the owner alone, which contradicted
     * {@code PermissionCode}: that enum marks {@code member:manage},
     * {@code member:read} and every {@code role:*} as
     * {@code businessStaffAssignable}, so an owner could grant a manager the
     * right to administer staff and roles and then watch the request refused
     * here. Authority comes from the permission, which {@code SecurityConfig}
     * checks; this only answers <em>whose</em> business.
     *
     * Largely redundant now — {@code SecurityConfig} pairs the same membership
     * test with each endpoint's permission through
     * {@code BusinessAccessAuthorizationManager}, so this runs second and
     * agrees. Worth deleting the next time these controllers are opened.
     */
    public void validateBusinessAccess(UUID businessId) {

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));

        UUID currentUserId = UUID.fromString(SecurityUtils.extractUserId());

        if (business.getKeycloakUserId().equals(currentUserId)) {
            return;
        }

        // Active only: a suspended member keeps their Keycloak role until the
        // token expires, so membership is re-read rather than trusted.
        boolean isActiveStaff = userProfileRepository.existsByUserIdAndBusinessIdAndStaffStatus(
                currentUserId, businessId, RecordStatus.ACTIVE);

        if (!isActiveStaff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not belong to this business");
        }
    }

    /**
     * @deprecated the name is a leftover — this no longer means owner-only.
     *             Kept because every call site is in a controller. Call
     *             {@link #validateBusinessAccess(UUID)} in new code.
     */
    @Deprecated
    public void validateBusinessOwner(UUID businessId) {
        validateBusinessAccess(businessId);
    }
}
