package kh.edu.istad.ite.features.business.service;

import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.business.dto.PublicStoreDetailResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreResponse;
import kh.edu.istad.ite.features.business.dto.SlugAvailabilityResponse;
import kh.edu.istad.ite.features.business.dto.StorefrontSlugRequest;
import kh.edu.istad.ite.features.business.dto.StorefrontStatusResponse;
import kh.edu.istad.ite.features.business.dto.StorefrontStatusResponse.StorefrontRequirement;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.mapper.StorefrontMapper;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.features.business.specification.PublicStoreSpecifications;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.mapper.ItemMapper;
import kh.edu.istad.ite.shared.enums.ItemStatus;

@Service
@RequiredArgsConstructor
public class StorefrontServiceImpl implements StorefrontService {

    private static final Pattern DNS_LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])?$");

    private final BusinessRepository businessRepository;
    private final BusinessHelper businessHelper;
    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;
    private final StorefrontMapper storefrontMapper;
    private final StorefrontProps storefrontProps;

    @Override
    @Transactional(readOnly = true)
    public StorefrontStatusResponse getMyStorefront() {
        return toStatusResponse(findMyBusiness());
    }

    @Override
    @Transactional
    public StorefrontStatusResponse enableStorefront() {
        Business business = findMyBusiness();

        // The platform can withhold the storefront entirely; the owner's own
        // checklist below only governs whether they are ready for it.
        businessHelper.requireFeature(business.getId(), BusinessFeature.STOREFRONT);

        List<StorefrontRequirement> requirements = evaluateRequirements(business);
        List<String> unmet = requirements.stream()
                .filter(requirement -> requirement.blocking() && !requirement.satisfied())
                .map(StorefrontRequirement::label)
                .toList();

        if (!unmet.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Storefront cannot be published yet: " + String.join("; ", unmet));
        }

        business.setIsListing(true);
        return toStatusResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public StorefrontStatusResponse disableStorefront() {
        Business business = findMyBusiness();
        business.setIsListing(false);
        return toStatusResponse(businessRepository.save(business));
    }

    @Override
    @Transactional
    public StorefrontStatusResponse changeSlug(StorefrontSlugRequest request) {
        Business business = findMyBusiness();
        String normalized = normalizeSlug(request.slug());

        String rejection = rejectionReason(normalized, business.getId());
        if (rejection != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, rejection);
        }

        business.setSlug(normalized);
        return toStatusResponse(businessRepository.save(business));
    }

    @Override
    @Transactional(readOnly = true)
    public SlugAvailabilityResponse checkSlugAvailability(String slug) {
        String normalized = normalizeSlug(slug);

        UUID currentBusinessId = businessRepository.findByKeycloakUserId(currentUserId())
                .map(Business::getId)
                .orElse(null);

        String rejection = rejectionReason(normalized, currentBusinessId);

        return new SlugAvailabilityResponse(
                normalized,
                rejection == null,
                rejection == null ? storefrontMapper.buildStorefrontUrl(normalized) : null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PublicStoreResponse> getPublicStores(
            UUID categoryId,
            String cityOrProvince,
            String keyword,
            Pageable pageable) {
        var spec = PublicStoreSpecifications.withFilters(categoryId, cityOrProvince, keyword);
        return businessRepository.findAll(spec, pageable).map(storefrontMapper::toPublicResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicStoreDetailResponse getPublicStoreBySlug(String slugOrId) {
        String normalized = normalizeSlug(slugOrId);

        org.springframework.data.jpa.domain.Specification<Business> spec = PublicStoreSpecifications.publiclyVisible()
                .and((root, query, cb) -> {
                    try {
                        UUID uuid = UUID.fromString(slugOrId);
                        return cb.or(
                                cb.equal(root.get("slug"), normalized),
                                cb.equal(root.get("id"), uuid));
                    } catch (IllegalArgumentException e) {
                        return cb.equal(root.get("slug"), normalized);
                    }
                });

        Business business = businessRepository.findOne(spec)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store has not been found"));

        return storefrontMapper.toPublicDetailResponse(business);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemResponse> getPublicStoreItems(String slugOrId) {
        String normalized = normalizeSlug(slugOrId);

        org.springframework.data.jpa.domain.Specification<Business> spec = PublicStoreSpecifications.publiclyVisible()
                .and((root, query, cb) -> {
                    try {
                        UUID uuid = UUID.fromString(slugOrId);
                        return cb.or(
                                cb.equal(root.get("slug"), normalized),
                                cb.equal(root.get("id"), uuid));
                    } catch (IllegalArgumentException e) {
                        return cb.equal(root.get("slug"), normalized);
                    }
                });

        Business business = businessRepository.findOne(spec)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store has not been found"));

        org.springframework.data.jpa.domain.Specification<Item> specItems = org.springframework.data.jpa.domain.Specification
                .where(kh.edu.istad.ite.features.catalog.specification.ItemSpecifications.hasBusinessId(business.getId()))
                .and(kh.edu.istad.ite.features.catalog.specification.ItemSpecifications.hasStatus(ItemStatus.ACTIVE))
                .and(kh.edu.istad.ite.features.catalog.specification.ItemSpecifications.isEnabledInChannelCodes(List.of("ONLINE", "WEB", "STOREFRONT")));

        List<Item> items = itemRepository.findAll(specItems);
        return items.stream()
                .map(itemMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PublicStoreResponse> getRecommendedStores(UUID categoryId, Pageable pageable) {
        return businessRepository.findRecommendedStores(categoryId, pageable)
                .map(storefrontMapper::toPublicResponse);
    }

    private List<StorefrontRequirement> evaluateRequirements(Business business) {
        List<StorefrontRequirement> requirements = new ArrayList<>();

        requirements.add(new StorefrontRequirement(
                "ACCOUNT_ACTIVE",
                "Business account must be active and not suspended",
                BusinessOwnerStatus.ACTIVE.equals(business.getStatus())
                        && Boolean.TRUE.equals(business.getIsEnabled())
                        && !Boolean.TRUE.equals(business.getIsClosed()),
                true));

        requirements.add(new StorefrontRequirement(
                "HAS_ITEM",
                "At least one item must exist",
                itemRepository.existsByBusiness_Id(business.getId()),
                true));

        requirements.add(new StorefrontRequirement(
                "VALID_SLUG",
                "Store address must be a valid, non-reserved slug",
                rejectionReason(business.getSlug(), business.getId()) == null,
                true));

        requirements.add(new StorefrontRequirement(
                "HAS_LOGO",
                "A logo helps customers recognise the store",
                StringUtils.hasText(business.getLogo()),
                false));

        requirements.add(new StorefrontRequirement(
                "HAS_ABOUT",
                "A short description improves the store page",
                StringUtils.hasText(business.getAbout()),
                false));

        requirements.add(new StorefrontRequirement(
                "HAS_PHONE",
                "A contact phone number lets customers reach the store",
                StringUtils.hasText(business.getPhoneNumber()),
                false));

        return requirements;
    }

    private StorefrontStatusResponse toStatusResponse(Business business) {
        List<StorefrontRequirement> requirements = evaluateRequirements(business);

        boolean readyToPublish = requirements.stream()
                .filter(StorefrontRequirement::blocking)
                .allMatch(StorefrontRequirement::satisfied);

        return new StorefrontStatusResponse(
                business.getId(),
                business.getSlug(),
                storefrontMapper.buildStorefrontUrl(business.getSlug()),
                Boolean.TRUE.equals(business.getIsListing()),
                readyToPublish,
                requirements);
    }

    /** Returns null when the slug is usable, otherwise a human readable reason. */
    private String rejectionReason(String slug, UUID excludedBusinessId) {
        if (!StringUtils.hasText(slug)) {
            return "Store address is required";
        }

        if (!DNS_LABEL.matcher(slug).matches()) {
            return "Store address may only contain lowercase letters, numbers and hyphens, "
                    + "and must not start or end with a hyphen";
        }

        if (storefrontProps.getReservedSlugs().contains(slug)) {
            return "Store address \"" + slug + "\" is reserved by the platform";
        }

        boolean taken = excludedBusinessId == null
                ? businessRepository.existsBySlug(slug)
                : businessRepository.existsBySlugAndIdNot(slug, excludedBusinessId);

        if (taken) {
            return "Store address \"" + slug + "\" is already taken";
        }

        return null;
    }

    private String normalizeSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }

    private Business findMyBusiness() {
        return businessRepository.findByKeycloakUserId(currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business has not been found"));
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityUtils.extractUserId());
    }
}
