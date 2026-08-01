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
        String storefrontUrl,
        BusinessSubCategoryResponse category,
        Boolean isClosed,
        Boolean isOpen,
        String discountLabel,
        String openTime,
        String closeTime
) {
}
