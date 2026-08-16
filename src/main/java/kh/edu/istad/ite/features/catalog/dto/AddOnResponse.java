package kh.edu.istad.ite.features.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AddOnResponse(
        UUID id,
        String name,
        String slug,
        UnitResponse baseUnit,
        BigDecimal usePerOrder,
        /** What one selection costs. Null until it has been priced. */
        BigDecimal price,
        List<AddOnUomConversionResponse> uomConversions,
        /**
         * Whether the item currently sells it. Only meaningful when this
         * add-on is read through an item; null in the shared library, which
         * has no one item to be on sale for.
         */
        Boolean available,
        String note
) {
}
