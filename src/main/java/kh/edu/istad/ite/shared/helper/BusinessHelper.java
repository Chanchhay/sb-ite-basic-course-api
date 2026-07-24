package kh.edu.istad.ite.shared.helper;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BusinessHelper {

    private final BusinessRepository businessRepository;

    public Business findOwnedBusiness(UUID businessId) {
        UUID keycloakUserId = AuthHelper.currentUserId();
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));

        if (!business.getKeycloakUserId().equals(keycloakUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have been forbidden");
        }

        return business;
    }

    public Business findOwnedBusinessOrNotFound(UUID businessId) {
        return businessRepository.findByIdAndKeycloakUserId(businessId, AuthHelper.currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }
}
