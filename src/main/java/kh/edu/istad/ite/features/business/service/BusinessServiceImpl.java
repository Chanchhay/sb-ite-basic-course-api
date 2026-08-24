package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.features.business.dto.CreateBusinessRequest;
import kh.edu.istad.ite.features.business.dto.SocialLinkRequest;
import kh.edu.istad.ite.features.business.dto.UpdateBusinessRequest;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.entity.BusinessCategory;
import kh.edu.istad.ite.features.business.entity.BusinessCurrency;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessCategoryRepository;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.SlugHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessServiceImpl implements BusinessService {

    private static final int SLUG_MAX_LENGTH = 63;
    private static final String SLUG_FALLBACK = "business";

    private final BusinessRepository businessRepository;
    private final BusinessCategoryRepository businessCategoryRepository;
    private final BusinessMapper businessMapper;
    private final BusinessHelper businessHelper;
    private final MinioService minioService;

    @Override
    @Transactional
    public BusinessResponse createBusiness(CreateBusinessRequest request) {
        UUID keycloakUserId = AuthHelper.currentUserId();
        if (businessRepository.existsByKeycloakUserId(keycloakUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business already exists for current user");
        }

        String name = TextHelper.trimRequired(request.name(), "name cannot be empty");
        BusinessCategory category = findSelectableCategory(UUID.fromString(request.categoryId()));

        Business business = new Business();
        business.setKeycloakUserId(keycloakUserId);
        business.setDisplayName(name);
        business.setSlug(generateUniqueSlug(name, null));
        business.setBusinessEmail(TextHelper.trimRequired(request.email(), "email cannot be empty"));
        business.setAddress(TextHelper.trimRequired(request.address(), "address cannot be empty"));
        business.setBusinessCategory(category);
        business.setProvisionedAt(LocalDateTime.now());
        business.setStatus(BusinessOwnerStatus.ACTIVE);
        business.setIsEnabled(true);
        business.setIsListing(false);
        business.setIsClosed(false);
        business.setBaseCurrency("USD");
        business.setDisplayCurrency("USD");
        business.getCurrencies().add(createDefaultCurrency(business));

        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessResponse getMyBusiness() {
        // Owner or staff. The dashboard resolves every other business id
        // through this one call, so answering only for owners left staff with
        // an application in which nothing loaded at all.
        return businessMapper.toResponse(businessHelper.currentBusiness());
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessResponse getBusiness(UUID businessId) {
        return businessMapper.toResponse(businessHelper.findOwnedBusinessOrNotFound(businessId));
    }

    @Override
    @Transactional
    public BusinessResponse updateBusiness(UUID businessId, UpdateBusinessRequest request) {
        Business business = businessHelper.findOwnedBusinessOrNotFound(businessId);

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
        if (request.about() != null) {
            business.setAbout(TextHelper.trimToNull(request.about()));
        }
        if (request.phoneNumber() != null) {
            business.setPhoneNumber(TextHelper.trimToNull(request.phoneNumber()));
        }
        if (request.googleMap() != null) {
            business.setGoogleMap(TextHelper.trimToNull(request.googleMap()));
        }
        if (request.cityOrProvince() != null) {
            business.setCityOrProvince(TextHelper.trimToNull(request.cityOrProvince()));
        }
        if (request.provinceName() != null) {
            business.setProvinceName(TextHelper.trimToNull(request.provinceName()));
            // The map picker setting a real province retires the old
            // free-text field for this business — nothing should keep
            // reading cityOrProvince once provinceName is driving it, so it
            // shouldn't keep sitting in the row looking authoritative.
            // Skipped only if this same request also touched cityOrProvince
            // directly, so an explicit set still wins.
            if (request.cityOrProvince() == null) {
                business.setCityOrProvince(null);
            }
        }
        if (request.districtName() != null) {
            business.setDistrictName(TextHelper.trimToNull(request.districtName()));
        }
        if (request.communeName() != null) {
            business.setCommuneName(TextHelper.trimToNull(request.communeName()));
        }
        if (request.latitude() != null) {
            business.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            business.setLongitude(request.longitude());
        }
        if (request.website() != null) {
            business.setWebsite(TextHelper.trimToNull(request.website()));
        }
        if (request.socialLinks() != null) {
            business.setSocialLinks(toSocialLinkMaps(request.socialLinks()));
        }
        if (request.openTime() != null) {
            business.setOpenTime(TextHelper.trimToNull(request.openTime()));
        }
        if (request.closeTime() != null) {
            business.setCloseTime(TextHelper.trimToNull(request.closeTime()));
        }

        return businessMapper.toResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public BusinessResponse uploadLogo(UUID businessId, MultipartFile file) {
        Business business = businessHelper.findOwnedBusinessOrNotFound(businessId);
        validateImage(file);

        String oldKey = business.getLogo();
        business.setLogo(minioService.uploadAsset(file));
        Business saved = businessRepository.save(business);

        if (oldKey != null) {
            minioService.deleteAsset(oldKey);
        }
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse deleteLogo(UUID businessId) {
        Business business = businessHelper.findOwnedBusinessOrNotFound(businessId);
        String oldKey = business.getLogo();
        business.setLogo(null);
        Business saved = businessRepository.save(business);

        if (oldKey != null) {
            minioService.deleteAsset(oldKey);
        }
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse uploadThumbnail(UUID businessId, MultipartFile file) {
        Business business = businessHelper.findOwnedBusinessOrNotFound(businessId);
        validateImage(file);

        String oldKey = business.getThumbnail();
        business.setThumbnail(minioService.uploadAsset(file));
        Business saved = businessRepository.save(business);

        if (oldKey != null) {
            minioService.deleteAsset(oldKey);
        }
        return businessMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BusinessResponse deleteThumbnail(UUID businessId) {
        Business business = businessHelper.findOwnedBusinessOrNotFound(businessId);
        String oldKey = business.getThumbnail();
        business.setThumbnail(null);
        Business saved = businessRepository.save(business);

        if (oldKey != null) {
            minioService.deleteAsset(oldKey);
        }
        return businessMapper.toResponse(saved);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file cannot be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }
    }

    @Override
    @Transactional
    public BusinessResponse deleteBusiness(UUID businessId) {
        Business business = businessHelper.findOwnedBusinessOrNotFound(businessId);
        business.setStatus(BusinessOwnerStatus.DELETED);
        business.setIsEnabled(false);
        business.setIsListing(false);

        return businessMapper.toResponse(businessRepository.save(business));
    }

    private BusinessCategory findSelectableCategory(UUID categoryId) {
        BusinessCategory category = businessCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business category has not been found"));

        if (category.getParentCategory() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business category must be a sub category");
        }

        return category;
    }

    private BusinessCurrency createDefaultCurrency(Business business) {
        BusinessCurrency currency = new BusinessCurrency();
        currency.setBusiness(business);
        currency.setCode("USD");
        currency.setName("United States Dollar");
        currency.setExchangeRate(BigDecimal.ONE.setScale(8));
        currency.setSymbol("$");
        currency.setDecimalPlaces((short) 2);
        return currency;
    }

    private String generateUniqueSlug(String name, UUID excludedBusinessId) {
        return SlugHelper.generateUniqueSlug(
                name,
                SLUG_FALLBACK,
                SLUG_MAX_LENGTH,
                slug -> slugExists(slug, excludedBusinessId)
        );
    }

    private boolean slugExists(String slug, UUID excludedBusinessId) {
        if (excludedBusinessId == null) {
            return businessRepository.existsBySlug(slug);
        }

        return businessRepository.existsBySlugAndIdNot(slug, excludedBusinessId);
    }

    private List<Map<String, String>> toSocialLinkMaps(List<SocialLinkRequest> socialLinks) {
        return socialLinks.stream()
                .map(socialLink -> Map.of(
                        "platform", TextHelper.trimRequired(socialLink.platform(), "platform cannot be empty"),
                        "url", TextHelper.trimRequired(socialLink.url(), "url cannot be empty")
                ))
                .toList();
    }
}
