package kh.edu.istad.ite.features.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BusinessCategoryUpsertRequest(
        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 255)
        String icon,
        UUID parentId
) {
}
