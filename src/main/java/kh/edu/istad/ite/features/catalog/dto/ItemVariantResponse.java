package kh.edu.istad.ite.features.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;

@Builder(toBuilder = true)
public record ItemVariantResponse(
        UUID id,
        String slug,
        String name,
        String sku,
        String barcode,
        String imageUrl,
        BigDecimal price,
        BigDecimal compareAtPrice,
        /** The size half of the pair — "Large". */
        String optionName,
        /** Which of the item's colours this row is; null when sold by size alone. */
        String colorValue,
        Boolean available,
        /**
         * How many of this option the asking channel may still sell.
         *
         * Null means the question was not asked or has no answer — a
         * non-physical item, or one the shop records no stock for. Zero is a
         * real answer and means sold out, so the two must not be conflated.
         */
        BigDecimal availableQuantity
) {
}
