package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.UnitCategory;

/** A unit a business defines for itself — "Sack", "Crate". */
public record BusinessUnitRequest(
        @NotBlank(message = "unit name cannot be empty")
        @Size(max = 50, message = "unit name must be at most 50 characters")
        String name,

        @NotBlank(message = "unit symbol cannot be empty")
        @Size(max = 20, message = "unit symbol must be at most 20 characters")
        String symbol,

        UnitCategory category,

        @Size(max = 255, message = "note must be at most 255 characters")
        String note
) {
    public BusinessUnitRequest {
        if (category == null) {
            category = UnitCategory.COUNT;
        }
    }
}
