package kh.edu.istad.ite.features.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ModifierOptionResponse(
        UUID id,
        String name,
        BigDecimal price,
        Boolean isDefault,
        Integer sortOrder
) {
}