package kh.edu.istad.ite.features.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.BusinessFeature;

public record FeatureToggleRequest(
        @NotNull
        BusinessFeature feature,

        @NotNull
        Boolean enabled,

        @Size(max = 500)
        String reason
) {
}
