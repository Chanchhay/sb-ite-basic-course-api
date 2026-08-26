package kh.edu.istad.ite.features.business.dto;

import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import kh.edu.istad.ite.shared.enums.TaxInclusionType;

import java.math.BigDecimal;
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
        String provinceName,
        String districtName,
        String communeName,
        BigDecimal latitude,
        BigDecimal longitude,
        String website,
        String email,
        Boolean isEnabled,
        Boolean isListing,
        Boolean isClosed,
        BusinessSubCategoryResponse category,
        String baseCurrency,
        String displayCurrency,
        List<Map<String, String>> socialLinks,
        String openTime,
        String closeTime,
        Boolean taxEnabled,
        BigDecimal taxRate,
        TaxInclusionType taxInclusionType,
        String taxLabel
) {
}
