package kh.edu.istad.ite.features.business.dto;

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
        String googleMap,
        String website,
        String storefrontUrl,
        String baseCurrency,
        String displayCurrency,
        BusinessSubCategoryResponse category,
        List<Map<String, String>> socialLinks,
        Boolean isClosed,
        Boolean isOpen,
        String discountLabel
) {
}
