package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.features.business.dto.CreateBusinessRequest;
import kh.edu.istad.ite.features.business.dto.SocialLinkRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessRequest;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessCategory;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessCategoryRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessServiceImpl implements BusinessService {

    private final BusinessRepository businessRepository;
    private final BusinessCategoryRepository businessCategoryRepository;
    private final BusinessMapper businessMapper;

    @Override
    @Transactional
    public BusinessResponse createBusiness(CreateBusinessRequest request) {
        UUID keycloakUserId = currentUserId();
        if (businessRepository.existsByKeycloakUserId(keycloakUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business already exists for current user");
        }

        BusinessCategory category = findSelectableCategory(UUID.fromString(request.categoryId()));

        Business business = new Business();
        business.setKeycloakUserId(keycloakUserId);
        business.setDisplayName(request.name().trim());
        business.setSlug(generateUniqueSlug(request.name(), null));
        business.setBusinessEmail(request.email().trim());
        business.setAddress(request.address().trim());
        business.setBusinessCategory(category);
        business.setProvisionedAt(LocalDateTime.now());
        business.setStatus(BusinessOwnerStatus.ACTIVE);
        business.setIsEnabled(true);
        business.setIsListing(false);
        business.setIsClosed(false);

        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessResponse getMyBusiness() {
        UUID keycloakUserId = currentUserId();
        Business business = businessRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
        return businessMapper.toResponse(business);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessResponse getBusiness(UUID businessId) {
        return businessMapper.toResponse(findOwnedBusiness(businessId));
    }

    @Override
    @Transactional
    public BusinessResponse updateBusiness(UUID businessId, UpdateBusinessRequest request) {
        Business business = findOwnedBusiness(businessId);

        if (StringUtils.hasText(request.name())) {
            String trimmedName = request.name().trim();
            if (!trimmedName.equals(business.getDisplayName())) {
                business.setDisplayName(trimmedName);
                business.setSlug(generateUniqueSlug(trimmedName, business.getId()));
            }
        }

        if (request.categoryId() != null) {
            business.setBusinessCategory(findSelectableCategory(UUID.fromString(request.categoryId())));
        }

        if (StringUtils.hasText(request.email())) {
            business.setBusinessEmail(request.email().trim());
        }
        if (StringUtils.hasText(request.address())) {
            business.setAddress(request.address().trim());
        }
        if (request.logo() != null) {
            business.setLogo(trimToNull(request.logo()));
        }
        if (request.thumbnail() != null) {
            business.setThumbnail(trimToNull(request.thumbnail()));
        }
        if (request.about() != null) {
            business.setAbout(trimToNull(request.about()));
        }
        if (request.phoneNumber() != null) {
            business.setPhoneNumber(trimToNull(request.phoneNumber()));
        }
        if (request.googleMap() != null) {
            business.setGoogleMap(trimToNull(request.googleMap()));
        }
        if (request.cityOrProvince() != null) {
            business.setCityOrProvince(trimToNull(request.cityOrProvince()));
        }
        if (request.website() != null) {
            business.setWebsite(trimToNull(request.website()));
        }
        if (request.socialLinks() != null) {
            business.setSocialLinks(toSocialLinkMaps(request.socialLinks()));
        }

        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public BusinessResponse deleteBusiness(UUID businessId) {
        Business business = findOwnedBusiness(businessId);
        business.setStatus(BusinessOwnerStatus.DELETED);
        business.setIsEnabled(false);
        business.setIsListing(false);

        return businessMapper.toResponse(businessRepository.save(business));
    }

    private Business findOwnedBusiness(UUID businessId) {
        UUID keycloakUserId = currentUserId();
        return businessRepository.findByIdAndKeycloakUserId(businessId, keycloakUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }

    private BusinessCategory findSelectableCategory(UUID categoryId) {
        BusinessCategory category = businessCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business category has not been found"));

        if (category.getParentCategory() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business category must be a sub category");
        }

        return category;
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityUtils.extractUserId());
    }

    private String generateUniqueSlug(String name, UUID excludedBusinessId) {
        String baseSlug = toSlugBase(name);
        String candidate = baseSlug;
        int suffix = 1;

        while (slugExists(candidate, excludedBusinessId)) {
            String suffixText = "-" + suffix;
            int baseMaxLength = 63 - suffixText.length();
            candidate = baseSlug.substring(0, Math.min(baseSlug.length(), baseMaxLength)).replaceAll("-$", "") + suffixText;
            suffix++;
        }

        return candidate;
    }

    private boolean slugExists(String slug, UUID excludedBusinessId) {
        if (excludedBusinessId == null) {
            return businessRepository.existsBySlug(slug);
        }

        return businessRepository.existsBySlugAndIdNot(slug, excludedBusinessId);
    }

    private String toSlugBase(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (!StringUtils.hasText(normalized)) {
            return "business";
        }

        return normalized.length() > 63 ? normalized.substring(0, 63).replaceAll("-$", "") : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private List<Map<String, String>> toSocialLinkMaps(List<SocialLinkRequest> socialLinks) {
        return socialLinks.stream()
                .map(socialLink -> Map.of(
                        "platform", socialLink.platform().trim(),
                        "url", socialLink.url().trim()
                ))
                .toList();
    }
}
