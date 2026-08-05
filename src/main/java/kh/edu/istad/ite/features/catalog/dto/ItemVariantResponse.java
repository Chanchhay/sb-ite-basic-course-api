package kh.edu.istad.ite.features.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemVariantResponse(
        UUID id,
        String slug,
        String name,
        BigDecimal price,
        Boolean available
) {
}
