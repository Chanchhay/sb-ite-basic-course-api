package kh.edu.istad.ite.features.business.dto;

import kh.edu.istad.ite.features.channel.dto.ChannelScheduleDto;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublicStoreDetailResponse(
        UUID id,
        String slug,
        String name,
        String logo,
        String thumbnail,
        String about,
        String phoneNumber,
        String address,
        String cityOrProvince,
        String provinceName,
        String districtName,
        Double latitude,
        Double longitude,
        /** Straight-line distance from the shopper's position; null unless they shared it. */
        Double distanceKm,
        String googleMap,
        String website,
        String storefrontUrl,
        String baseCurrency,
        String displayCurrency,
        BusinessSubCategoryResponse category,
        List<Map<String, String>> socialLinks,
        Boolean isClosed,
        Boolean isOpen,
        String discountLabel,
        String openTime,
        String closeTime,
        /**
         * The hours the shop set for its Online Store, as the checkout
         * enforces them. Null when it keeps none, which means always open.
         */
        ChannelScheduleDto onlineHours,
        /** Whether those hours say it is taking orders this minute. */
        Boolean openNow,
        /** What it is open for today, for a line worth reading. */
        String hoursToday,
        /**
         * The store's single tax rule, so the cart and checkout can show the
         * same tax the order will actually be charged instead of a bare
         * subtotal. Mirrors {@code TaxCalculator} — see Business Settings.
         */
        Boolean taxEnabled,
        BigDecimal taxRate,
        TaxInclusionType taxInclusionType,
        String taxLabel
) {
}
