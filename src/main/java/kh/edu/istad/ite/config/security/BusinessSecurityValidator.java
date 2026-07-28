package kh.edu.istad.ite.config.security;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BusinessSecurityValidator {

    private final BusinessRepository businessRepository;
    private final CurrentAuthorizationContext currentAuthorizationContext;

    public void validateBusinessOwner(UUID businessId) {

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));

        UUID currentUserId = UUID.fromString(SecurityUtils.extractUserId());

        if (!business.getKeycloakUserId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the business owner or a SUPER_ADMIN can perform this action");
        }
    }
}
