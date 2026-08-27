package kh.edu.istad.ite.features.catalog.dto;

import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;

@Builder(toBuilder = true)
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

        /**
         * What this item cost before a discount was applied to it, filled
         * in by the storefront when one is running and null everywhere
         * else. Not a price anyone sets: an item is worth what it is
         * priced at, and a struck-through figure has to be a real former
         * price rather than a number somebody typed to look generous.
         */
        BigDecimal compareAtPrice,
        ItemType itemType,
        Boolean trackInventory,
        List<ItemAttributeResponse> attributes,
        /** The colours this item comes in, declared once for every size. */
        List<ItemColorResponse> colors,
        List<DescriptionBlockResponse> descriptionBlocks,
        List<ItemVariantResponse> variants,
        List<AddOnResponse> addOns,
        List<ItemUomConversionResponse> uomConversions,
        Integer lowStockDefault,
        ItemStatus status,
        /**
         * How many of this item the asking channel may still sell, summed over
         * its options — the cap is counted per option, so the item's own figure
         * is the total of what each of them may sell.
         *
         * Only the storefront fills this in; every other reader leaves it null,
         * which means "not asked" rather than "none left".
         */
        BigDecimal availableQuantity
) {
}
