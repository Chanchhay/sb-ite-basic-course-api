package kh.edu.istad.ite.features.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StorefrontSlugRequest(
        @NotBlank
        @Size(min = 3, max = 63)
        String slug
) {
}
