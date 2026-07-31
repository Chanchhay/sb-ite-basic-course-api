package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record ItemResponse(
        UUID id,
        UUID businessId,
        ItemSubGroupResponse itemGroup,
        UnitResponse unit,
        String slug,
        String name,
        String sku,
        String code,
        String description,
        String badge,
        List<ItemImageResponse> images,
        String barcode,
        BigDecimal price,
        ItemType itemType,
        Map<String, Object> attributes,
        List<ItemVariantResponse> variants,
        Integer lowStockDefault,
        ItemStatus status
) {
}
