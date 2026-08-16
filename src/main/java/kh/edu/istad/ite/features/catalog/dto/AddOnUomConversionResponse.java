package kh.edu.istad.ite.features.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AddOnUomConversionResponse(
        UUID id,
        UnitResponse unit,
        /** Base units per one of {@code unit}. */
        BigDecimal factor
) {
}
