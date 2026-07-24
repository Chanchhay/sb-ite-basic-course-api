package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.ProductStatus;
import kh.edu.istad.ite.shared.enums.ProductType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID businessId,
        ProductSubCategoryResponse category,
        UnitResponse unit,
        String slug,
        String name,
        String sku,
        String code,
        String description,
        String imageUrl,
        String barcode,
        BigDecimal price,
        ProductType itemType,
        Map<String, Object> attributes,
        List<ProductVariantResponse> variants,
        Integer lowStockDefault,
        ProductStatus status
) {
}
