package kh.edu.istad.ite.features.admin.dto.response;

import kh.edu.istad.ite.shared.enums.BusinessFeature;

import java.time.LocalDateTime;
import java.util.UUID;

public record PlatformFeatureResponse(
        BusinessFeature feature,
        String label,
        String description,
        boolean enabled,
        String disabledReason,
        UUID disabledBy,
        LocalDateTime disabledAt
) {
}
