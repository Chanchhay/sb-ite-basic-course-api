package kh.edu.istad.ite.features.business.mapper;

import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.features.business.dto.PublicStoreDetailResponse;
import kh.edu.istad.ite.features.business.dto.PublicStoreResponse;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.channel.dto.ChannelScheduleDto;
import kh.edu.istad.ite.features.channel.service.ChannelPriceResolver;
import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.repository.DiscountRepository;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.shared.enums.DiscountRuleType;
import kh.edu.istad.ite.shared.enums.DiscountScope;
import kh.edu.istad.ite.shared.enums.DiscountType;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StorefrontMapper {

    private final StorefrontProps storefrontProps;
    private final BusinessMapper businessMapper;
    private final MinioService minioService;
    private final DiscountRepository discountRepository;
    private final ChannelPriceResolver channelPriceResolver;

    /** The seeded channel the online store trades as. */
    private static final String WEB_CHANNEL_CODE = "WEB";

    public String buildStorefrontUrl(String slug) {
        if (slug == null) {
            return null;
        }

        if (storefrontProps.isSubdomainEnabled()) {
            return storefrontProps.getProtocol() + "://" + slug + "." + storefrontProps.getBaseDomain();
        }

        return storefrontProps.getProtocol() + "://" + storefrontProps.getBaseDomain()
                + storefrontProps.getPathPrefix() + "/" + slug;
    }

    private String toPublicUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return minioService.getPublicUrl(key);
    }

    /** provinceName once a business has been through the location rework, its old cityOrProvince text otherwise — same fallback the filter itself matches on. */
    private String effectiveProvinceName(Business business) {
        return business.getProvinceName() != null ? business.getProvinceName() : business.getCityOrProvince();
    }

    public String resolveDiscountLabel(Business business) {
        if (business == null || business.getId() == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = now.getDayOfWeek();

        List<Discount> activeDiscounts = discountRepository.findActiveDiscountsByBusinessId(
                business.getId(),
                RecordStatus.ACTIVE,
                now
        ).stream()
         .filter(d -> !Boolean.TRUE.equals(d.getRequiresCoupon()))
         .filter(d -> d.getApplicableChannels() == null
                 || d.getApplicableChannels().isEmpty()
                 || d.getApplicableChannels().contains(OrderChannel.WEB))
         .filter(d -> d.getSelectedDays() == null
                 || d.getSelectedDays().isEmpty()
                 || d.getSelectedDays().contains(today))
         .toList();

        if (activeDiscounts == null || activeDiscounts.isEmpty()) {
            return null;
        }

        List<Discount> generalDiscounts = activeDiscounts.stream()
                .filter(d -> d.getScope() == DiscountScope.ORDER || d.getScope() == DiscountScope.ALL_ITEMS)
                .toList();

        List<Discount> candidates = generalDiscounts.isEmpty() ? activeDiscounts : generalDiscounts;

        Discount primary = candidates.stream()
                .filter(d -> d.getType() == DiscountType.PERCENTAGE)
                .max((d1, d2) -> (d1.getValue() != null ? d1.getValue() : BigDecimal.ZERO)
                        .compareTo(d2.getValue() != null ? d2.getValue() : BigDecimal.ZERO))
                .orElse(candidates.get(0));

        if (primary.getName() != null && !primary.getName().isBlank()) {
            return primary.getName();
        }

        if (primary.getRuleType() == DiscountRuleType.BUY_X_GET_Y) {
            int buy = primary.getBuyQuantity() != null ? primary.getBuyQuantity() : 1;
            int get = primary.getGetQuantity() != null ? primary.getGetQuantity() : 1;
            return "Buy " + buy + " Get " + get;
        } else if (primary.getType() == DiscountType.PERCENTAGE && primary.getValue() != null) {
            String pct = primary.getValue().stripTrailingZeros().toPlainString();
            return (generalDiscounts.isEmpty() ? "Up to " : "") + pct + "% OFF";
        } else if (primary.getType() == DiscountType.FIXED_AMOUNT && primary.getValue() != null) {
            String amt = primary.getValue().stripTrailingZeros().toPlainString();
            return (generalDiscounts.isEmpty() ? "Up to $" : "$") + amt + " OFF";
        }
        return primary.getName();
    }

    /**
     * The hours the shop set for its Online Store.
     *
     * Read from the WEB channel rather than from the business's own open and
     * close times: those are the shopfront's hours, and a shop can perfectly
     * well take web orders after the doors are locked — or stop taking them
     * before. The channel's schedule is what the checkout enforces, so it is
     * the only one worth showing.
     */
    private ChannelScheduleDto onlineHoursOf(Business business) {
        return channelPriceResolver.scheduleFor(business.getId(), WEB_CHANNEL_CODE);
    }

    /** Whether web orders are being taken right now: the switch and the hours. */
    private boolean isTakingWebOrders(Business business) {
        if (Boolean.TRUE.equals(business.getIsClosed())) {
            return false;
        }

        ChannelScheduleDto hours = onlineHoursOf(business);

        return hours == null || hours.isOpenAt(LocalDateTime.now());
    }

    public PublicStoreResponse toPublicResponse(Business business) {
        return toPublicResponse(business, null);
    }

    /** {@code distanceKm} is null unless the caller supplied their own position. */
    public PublicStoreResponse toPublicResponse(Business business, Double distanceKm) {
        boolean isClosed = Boolean.TRUE.equals(business.getIsClosed());
        return new PublicStoreResponse(
                business.getId(),
                business.getSlug(),
                business.getDisplayName(),
                toPublicUrl(business.getLogo()),
                toPublicUrl(business.getThumbnail()),
                business.getAbout(),
                business.getAddress(),
                business.getCityOrProvince(),
                effectiveProvinceName(business),
                business.getLatitude() == null ? null : business.getLatitude().doubleValue(),
                business.getLongitude() == null ? null : business.getLongitude().doubleValue(),
                distanceKm,
                buildStorefrontUrl(business.getSlug()),
                businessMapper.toSubCategoryResponse(business.getBusinessCategory()),
                isClosed,
                isTakingWebOrders(business),
                resolveDiscountLabel(business),
                business.getOpenTime(),
                business.getCloseTime()
        );
    }

    public PublicStoreDetailResponse toPublicDetailResponse(Business business) {
        return toPublicDetailResponse(business, null);
    }

    /** {@code distanceKm} is null unless the caller supplied their own position. */
    public PublicStoreDetailResponse toPublicDetailResponse(Business business, Double distanceKm) {
        boolean isClosed = Boolean.TRUE.equals(business.getIsClosed());
        ChannelScheduleDto onlineHours = onlineHoursOf(business);
        boolean openNow = isTakingWebOrders(business);
        return new PublicStoreDetailResponse(
                business.getId(),
                business.getSlug(),
                business.getDisplayName(),
                toPublicUrl(business.getLogo()),
                toPublicUrl(business.getThumbnail()),
                business.getAbout(),
                business.getPhoneNumber(),
                business.getAddress(),
                business.getCityOrProvince(),
                effectiveProvinceName(business),
                business.getDistrictName(),
                business.getLatitude() == null ? null : business.getLatitude().doubleValue(),
                business.getLongitude() == null ? null : business.getLongitude().doubleValue(),
                distanceKm,
                business.getGoogleMap(),
                business.getWebsite(),
                buildStorefrontUrl(business.getSlug()),
                business.getBaseCurrency(),
                business.getDisplayCurrency(),
                businessMapper.toSubCategoryResponse(business.getBusinessCategory()),
                business.getSocialLinks(),
                isClosed,
                openNow,
                resolveDiscountLabel(business),
                business.getOpenTime(),
                business.getCloseTime(),
                onlineHours,
                openNow,
                onlineHours == null
                        ? null
                        : onlineHours.describeDay(LocalDateTime.now().getDayOfWeek())
        );
    }
}