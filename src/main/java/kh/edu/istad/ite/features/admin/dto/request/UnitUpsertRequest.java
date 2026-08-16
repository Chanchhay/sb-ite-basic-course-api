package kh.edu.istad.ite.features.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.UnitCategory;

public record UnitUpsertRequest(
        @NotBlank
        @Size(max = 50)
        String name,

        @Size(max = 20)
        String symbol,

        UnitCategory category,

        @Size(max = 255)
        String note
) {
    public UnitUpsertRequest {
        if (category == null) {
            category = UnitCategory.COUNT;
        }
    }
}
