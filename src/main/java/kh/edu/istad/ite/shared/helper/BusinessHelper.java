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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class BusinessHelper {

    private final BusinessRepository businessRepository;
    private final UserProfileRepository userProfileRepository;
    private final BusinessFeatureFlagRepository featureFlagRepository;
    private final PlatformFeatureFlagRepository platformFeatureFlagRepository;

    @Deprecated
    public Business findOwnedBusiness(UUID businessId) {
        return findAccessibleBusiness(businessId);
    }

    public Business findBusinessForOperator(UUID businessId) {
        if (!AuthHelper.isPlatformOperator()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have been forbidden");
        }

        Business business = findBusiness(businessId);

        log.info("Platform operator {} acting on business {}",
                AuthHelper.currentUserId(), businessId);

        return business;
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

    @Deprecated
    public Business findOwnedBusinessOrNotFound(UUID businessId) {
        return findAccessibleBusiness(businessId);
    }

    public Business currentBusiness() {
        return currentBusinessOrEmpty()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Business has not been found"));
    }

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
