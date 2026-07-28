package kh.edu.istad.ite.features.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlatformFeatureToggleRequest(
        @NotNull
        Boolean enabled,

        @Size(max = 500)
        String reason
) {
}
