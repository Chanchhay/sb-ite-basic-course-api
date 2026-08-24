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
import kh.edu.istad.ite.shared.helper.GeoDistanceHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.mapper.ItemMapper;
import kh.edu.istad.ite.shared.enums.ItemStatus;

import kh.edu.istad.ite.features.discount.service.DiscountService;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.ItemType;
import kh.edu.istad.ite.features.catalog.dto.ItemVariantResponse;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.channel.service.ChannelPriceResolver;
import kh.edu.istad.ite.features.channel.service.ItemChannelStockService;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StorefrontServiceImpl implements StorefrontService {

    private static final Pattern DNS_LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])?$");

    /** The seeded channel the online store trades as. Matches {@link OrderChannel#WEB}. */
    private static final String WEB_CHANNEL_CODE = OrderChannel.WEB.name();

    private final BusinessRepository businessRepository;
    private final BusinessHelper businessHelper;
    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;
    private final StorefrontMapper storefrontMapper;
    private final StorefrontProps storefrontProps;
    private final DiscountService discountService;
    private final ChannelPriceResolver channelPriceResolver;
    private final ItemChannelStockService itemChannelStockService;
    private final StockEntryService stockEntryService;

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

        // A caller with no business at all is allowed here: rejectionReason
        // treats null as "nothing of mine to clash with".
        UUID currentBusinessId = businessHelper.currentBusinessOrEmpty()
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
            String province,
            String district,
            String cityOrProvince,
            String keyword,
            Double lat,
            Double lng,
            Pageable pageable) {
        var spec = PublicStoreSpecifications.withFilters(categoryId, province, district, cityOrProvince, keyword);

        if (lat == null || lng == null) {
            return businessRepository.findAll(spec, pageable).map(storefrontMapper::toPublicResponse);
        }

        // Distance ranks the whole filtered set, so it has to be sorted before
        // paging — there's nowhere in the DB query to compute it yet, and
        // paging first would only sort each page instead of the results. Fine
        // at today's store counts; a count large enough for this full scan to
        // matter is the cue to move it into a native Haversine query instead.
        List<Business> matches = businessRepository.findAll(spec);
        List<java.util.Map.Entry<Business, Double>> ranked = matches.stream()
                .map(business -> java.util.Map.entry(business, distanceKm(business, lat, lng)))
                .sorted(Comparator.comparing(
                        entry -> entry.getValue() == null ? Double.MAX_VALUE : entry.getValue()))
                .toList();

        int start = Math.min((int) pageable.getOffset(), ranked.size());
        int end = Math.min(start + pageable.getPageSize(), ranked.size());
        List<PublicStoreResponse> pageContent = ranked.subList(start, end).stream()
                .map(entry -> storefrontMapper.toPublicResponse(entry.getKey(), entry.getValue()))
                .toList();

        return new PageImpl<>(pageContent, pageable, ranked.size());
    }

    /** Null when the business hasn't dropped a map pin yet — nothing to rank it by. */
    private Double distanceKm(Business business, double lat, double lng) {
        if (business.getLatitude() == null || business.getLongitude() == null) {
            return null;
        }
        return GeoDistanceHelper.haversineKm(
                lat, lng, business.getLatitude().doubleValue(), business.getLongitude().doubleValue());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicStoreDetailResponse getPublicStoreBySlug(String slugOrId, Double lat, Double lng) {
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

        Double distance = (lat == null || lng == null) ? null : distanceKm(business, lat, lng);
        return storefrontMapper.toPublicDetailResponse(business, distance);
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
                // What the shop published to its Online Store, and nothing
                // else. WEB is seeded like every other channel, so the toggle
                // in the back office is what decides this — listing the till's
                // items instead made that toggle decoration and let a shop's
                // counter-only lines leak onto the web.
                .and(kh.edu.istad.ite.features.catalog.specification.ItemSpecifications
                        .isEnabledInChannelCodes(List.of(WEB_CHANNEL_CODE)));

        List<Item> items = itemRepository.findAll(specItems);
        return items.stream()
                .map(item -> {
                    // What the web charges, not what the business charges: the
                    // checkout prices every line the same way, and quoting one
                    // number while billing another is the bug this avoids.
                    ItemResponse base = withWebAvailability(
                            channelPriceResolver.atChannelPrices(
                                    itemMapper.toResponse(item), business.getId(), WEB_CHANNEL_CODE),
                            item,
                            business.getId());

                    if (base.price() == null) return base;

                    try {
                        List<DiscountResponse> applicable = discountService.findApplicableDiscounts(
                                business.getId(),
                                OrderChannel.WEB,
                                item.getId(),
                                item.getItemGroup() != null ? item.getItemGroup().getId() : null
                        );
                        
                        if (!applicable.isEmpty()) {
                            // Apply the first/best discount for display
                            DiscountResponse best = applicable.get(0);
                            BigDecimal originalPrice = base.price();
                            BigDecimal discountAmount = BigDecimal.ZERO;
                            
                            if (best.type() == DiscountType.PERCENTAGE && best.value() != null) {
                                discountAmount = originalPrice.multiply(best.value()).divide(new BigDecimal("100"));
                            } else if (best.type() == DiscountType.FIXED_AMOUNT && best.value() != null) {
                                discountAmount = best.value();
                            }
                            
                            BigDecimal newPrice = originalPrice.subtract(discountAmount);
                            if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
                                newPrice = BigDecimal.ZERO;
                            }
                            
                            // Prevent overwriting if already discounted or if new price is not actually cheaper
                            if (newPrice.compareTo(originalPrice) < 0) {
                                String computedBadge = best.type() == DiscountType.PERCENTAGE ? 
                                    best.value().stripTrailingZeros().toPlainString() + "% OFF" : 
                                    best.name();
                                    
                                return base.toBuilder()
                                    .price(newPrice)
                                    .compareAtPrice(originalPrice)
                                    .badge(base.badge() != null && !base.badge().isBlank() ? base.badge() : computedBadge)
                                    .build();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore discount errors for storefront display
                    }
                    return base;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PublicStoreResponse> getRecommendedStores(UUID categoryId, Double lat, Double lng, Pageable pageable) {
        // Ranking here stays sales/recency-based — "recommended" isn't
        // "nearest" — this only annotates each card with its distance for
        // display, same as the plain listing does.
        if (lat == null || lng == null) {
            return businessRepository.findRecommendedStores(categoryId, pageable)
                    .map(storefrontMapper::toPublicResponse);
        }
        return businessRepository.findRecommendedStores(categoryId, pageable)
                .map(business -> storefrontMapper.toPublicResponse(business, distanceKm(business, lat, lng)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctProvinces() {
        return businessRepository.findDistinctProvinceNames();
    }

    /**
     * How many of each thing the web may still sell.
     *
     * Counted per option, because that is how stock is counted: ten Smalls on
     * the shelf says nothing about the Large. The item's own figure is the sum
     * of its options, so a shopper looking at the card sees what the whole
     * listing can supply and the picker inside it sees which sizes are gone.
     *
     * Left null when there is nothing to report — a service the shop does not
     * count, or an item it records no stock for. Zero means sold out, and the
     * two must stay distinguishable or every untracked item reads as empty.
     */
    private ItemResponse withWebAvailability(ItemResponse response, Item item, UUID businessId) {
        if (!ItemType.PHYSICAL.equals(item.getItemType())) {
            return response;
        }

        List<ItemVariantResponse> variants = response.variants();

        if (variants == null || variants.isEmpty()) {
            return response.toBuilder()
                    .availableQuantity(webAvailability(businessId, item, null))
                    .build();
        }

        Map<UUID, ItemVariant> byId = item.getVariants().stream()
                .collect(Collectors.toMap(ItemVariant::getId, Function.identity(), (a, b) -> a));

        BigDecimal total = null;
        List<ItemVariantResponse> answered = new ArrayList<>(variants.size());

        for (ItemVariantResponse variant : variants) {
            BigDecimal available = webAvailability(businessId, item, byId.get(variant.id()));
            answered.add(variant.toBuilder().availableQuantity(available).build());

            if (available != null) {
                total = (total == null ? BigDecimal.ZERO : total).add(available);
            }
        }

        return response.toBuilder().variants(answered).availableQuantity(total).build();
    }

    /** Null when the shop keeps no stock record for this option — no ceiling to report. */
    private BigDecimal webAvailability(UUID businessId, Item item, ItemVariant variant) {
        StockSummaryResponse summary = stockEntryService.findAvailableStock(
                businessId, item.getId(), variant == null ? null : variant.getId());

        if (summary == null || summary.lastEntryId() == null) {
            return null;
        }

        BigDecimal onHand = summary.quantityOnHand() == null ? BigDecimal.ZERO : summary.quantityOnHand();

        // An oversold shelf or a spent allocation can both go negative; the
        // storefront has no use for "minus two" and would print it.
        return itemChannelStockService
                .availableFor(item, variant, OrderChannel.WEB, onHand)
                .max(BigDecimal.ZERO);
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
        return businessHelper.currentBusiness();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityUtils.extractUserId());
    }
}
