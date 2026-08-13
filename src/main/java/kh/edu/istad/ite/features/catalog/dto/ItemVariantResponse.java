package kh.edu.istad.ite.features.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemVariantResponse(
        UUID id,
        String slug,
        String name,
        String sku,
        String barcode,
        String imageUrl,
        BigDecimal price,
        Boolean available
) {
}
