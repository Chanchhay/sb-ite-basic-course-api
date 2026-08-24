package kh.edu.istad.ite.features.business.dto;

import java.util.UUID;

public record PublicStoreResponse(
        UUID id,
        String slug,
        String name,
        String logo,
        String thumbnail,
        String about,
        String cityOrProvince,
        String provinceName,
        Double latitude,
        Double longitude,
        /** Straight-line distance from the shopper's position; null unless they shared it. */
        Double distanceKm,
        String storefrontUrl,
        BusinessSubCategoryResponse category,
        Boolean isClosed,
        /**
         * Whether the shop is taking web orders right now — the manual switch
         * and the Online Store's hours together, since a card that says "Open"
         * on a shop the checkout will refuse is worse than no card at all.
         */
        Boolean isOpen,
        String discountLabel,
        String openTime,
        String closeTime
) {
}
