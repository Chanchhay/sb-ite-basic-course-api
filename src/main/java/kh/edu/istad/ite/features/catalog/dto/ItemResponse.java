package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;

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
        String imageUrl,
        List<ItemImageResponse> images,
        String badge,
        String barcode,
        BigDecimal price,
        BigDecimal compareAtPrice,
        ItemType itemType,
        List<ItemAttributeResponse> attributes,
        List<DescriptionBlockResponse> descriptionBlocks,
        List<ItemVariantResponse> variants,
        Integer lowStockDefault,
        ItemStatus status
) {
}
