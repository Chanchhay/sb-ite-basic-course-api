package kh.edu.istad.ite.shared.helper;

import kh.edu.istad.ite.features.admin.repository.PlatformFeatureFlagRepository;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessFeatureFlagRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BusinessHelper {

    private final BusinessRepository businessRepository;
    private final UserProfileRepository userProfileRepository;
    private final BusinessFeatureFlagRepository featureFlagRepository;
    private final PlatformFeatureFlagRepository platformFeatureFlagRepository;

    /**
     * @deprecated ownership is the wrong question, and asking it here broke
     *             every staff account: a manager granted {@code item:update}
     *             still owns no business, so this refused them. What may be
     *             done is decided by the permission, which {@code SecurityConfig}
     *             checks; all this layer needs to establish is that the caller
     *             belongs to the business. Delegates to
     *             {@link #findAccessibleBusiness(UUID)}; call that directly in
     *             new code.
     *             <p>
     *             Nothing is loosened by this. The two operations that really
     *             are owner-only — creating and deleting a business — are held
     *             back by {@code PermissionCode}, which marks
     *             {@code business:create} and {@code business:delete} as not
     *             assignable to business staff, so no staff role can carry them.
     */
    @Deprecated
    public Business findOwnedBusiness(UUID businessId) {
        return findAccessibleBusiness(businessId);
    }

    /** The business, provided the caller owns it or is active staff there. */
    public Business findAccessibleBusiness(UUID businessId) {
        UUID keycloakUserId = AuthHelper.currentUserId();
        Business business = findBusiness(businessId);

        if (business.getKeycloakUserId().equals(keycloakUserId)) {
            return business;
        }

        boolean activeStaff = userProfileRepository.existsByUserIdAndBusinessIdAndStaffStatus(
                keycloakUserId, businessId, RecordStatus.ACTIVE);

        if (!activeStaff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have been forbidden");
        }

        return business;
    }

    /** @deprecated see {@link #findOwnedBusiness(UUID)}; same reasoning. */
    @Deprecated
    public Business findOwnedBusinessOrNotFound(UUID businessId) {
        return findAccessibleBusiness(businessId);
    }

    /**
     * The business the caller works in, without being told which.
     *
     * Backs {@code GET /api/v1/businesses/me} and every "my settings" screen.
     * An owner is found by ownership; a staff member by the membership
     * {@code StaffManagementService} writes onto their {@code UserProfile}.
     * Looking only at ownership — which is what this used to do everywhere —
     * gave staff a 404 for the business they work in, and since the dashboard
     * resolves every other id through this call, it left them with an
     * application in which nothing at all would load.
     */
    public Business currentBusiness() {
        return currentBusinessOrEmpty()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Business has not been found"));
    }

    /** As {@link #currentBusiness()}, for callers that treat "none" as an answer. */
    public Optional<Business> currentBusinessOrEmpty() {
        UUID keycloakUserId = AuthHelper.currentUserId();

        return businessRepository.findByKeycloakUserId(keycloakUserId)
                .or(() -> userProfileRepository
                        .findFirstByUserIdAndStaffStatusAndBusinessIsNotNull(
                                keycloakUserId, RecordStatus.ACTIVE)
                        .map(UserProfile::getBusiness));
    }

    public void requireFeature(UUID businessId, BusinessFeature feature) {
        if (isDisabledPlatformWide(feature)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    feature.getLabel() + " has been switched off platform-wide");
        }

        boolean disabled = featureFlagRepository
                .existsByBusinessIdAndFeatureAndEnabledFalse(businessId, feature);

        if (disabled) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    feature.getLabel() + " has been disabled for this business by the platform");
        }
    }

    public boolean isFeatureEnabled(UUID businessId, BusinessFeature feature) {
        if (isDisabledPlatformWide(feature)) {
            return false;
        }

        return !featureFlagRepository.existsByBusinessIdAndFeatureAndEnabledFalse(businessId, feature);
    }

    private boolean isDisabledPlatformWide(BusinessFeature feature) {
        return platformFeatureFlagRepository.existsByFeatureAndEnabledFalse(feature);
    }

    public Business findBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }
}
