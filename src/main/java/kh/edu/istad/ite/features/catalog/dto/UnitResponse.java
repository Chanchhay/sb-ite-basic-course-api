package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.UnitCategory;

import java.util.UUID;

public record UnitResponse(
        UUID id,
        String name,
        String slug,
        String symbol,
        UnitCategory category,
        /** True for a platform unit: selectable everywhere, editable nowhere. */
        boolean system,
        String note
) {
}
