package kh.edu.istad.ite.features.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemUomConversionResponse(
        UUID id,
        UnitResponse unit,
        /** The option it is for. Null on an item with no options. */
        UUID variantId,
        String variantName,
        /** Base units per one of {@code unit}. */
        BigDecimal factor,
        /** What one of this unit sells for. Null when it is not sold this way. */
        BigDecimal price
) {
}
