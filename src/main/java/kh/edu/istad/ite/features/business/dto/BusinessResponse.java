package kh.edu.istad.ite.features.business.dto;

import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BusinessResponse(
        UUID id,
        UUID keycloakUserId,
        String slug,
        String name,
        BusinessOwnerStatus status,
        LocalDateTime provisionedAt,
        String logo,
        String thumbnail,
        String about,
        String phoneNumber,
        String googleMap,
        String address,
        String cityOrProvince,
        String website,
        String email,
        Boolean isEnabled,
        Boolean isListing,
        Boolean isClosed,
        BusinessSubCategoryResponse category,
        String baseCurrency,
        String displayCurrency,
        List<Map<String, String>> socialLinks
) {
}
