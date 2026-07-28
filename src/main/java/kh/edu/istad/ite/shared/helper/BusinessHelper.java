package kh.edu.istad.ite.shared.helper;

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
public class BusinessHelper {

    private final BusinessRepository businessRepository;
    private final UserProfileRepository userProfileRepository;

    public Business findOwnedBusiness(UUID businessId) {
        UUID keycloakUserId = AuthHelper.currentUserId();
        Business business = findBusiness(businessId);

        if (!business.getKeycloakUserId().equals(keycloakUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have been forbidden");
        }

        return business;
    }


    public Business findAccessibleBusiness(UUID businessId) {
        UUID keycloakUserId = AuthHelper.currentUserId();
        Business business = findBusiness(businessId);

        if (business.getKeycloakUserId().equals(keycloakUserId)) {
            return business;
        }

        boolean activeStaff = userProfileRepository.existsByUserIdAndBusinessIdAndUserStatus(
                keycloakUserId, businessId, RecordStatus.ACTIVE);

        if (!activeStaff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have been forbidden");
        }

        return business;
    }

    public Business findOwnedBusinessOrNotFound(UUID businessId) {
        return businessRepository.findByIdAndKeycloakUserId(businessId, AuthHelper.currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }

    private Business findBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }
}
