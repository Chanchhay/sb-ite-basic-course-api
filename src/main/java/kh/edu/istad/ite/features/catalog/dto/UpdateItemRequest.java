package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpdateItemRequest(
        UUID itemGroupId,

        UUID unitId,

        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        @Size(max = 100, message = "sku must be at most 100 characters")
        String sku,

        @Size(max = 100, message = "code must be at most 100 characters")
        String code,

        String description,

        @Size(max = 255, message = "imageUrl must be at most 255 characters")
        String imageUrl,

        @Size(max = 8, message = "images list must have at most 8 items")
        List<@Size(max = 255, message = "each image url must be at most 255 characters") String> images,

        @Size(max = 40, message = "badge must be at most 40 characters")
        String badge,

        @Size(max = 100, message = "barcode must be at most 100 characters")
        String barcode,

        @DecimalMin(value = "0.0", inclusive = true, message = "price must be at least zero")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 10 integer digits and 2 decimal places")
        BigDecimal price,

        @DecimalMin(value = "0.0", inclusive = true, message = "compareAtPrice must be at least zero")
        @Digits(integer = 10, fraction = 2, message = "compareAtPrice must have at most 10 integer digits and 2 decimal places")
        BigDecimal compareAtPrice,

        ItemType itemType,

        List<@Valid ItemAttributeRequest> attributes,

        @Size(max = 30, message = "descriptionBlocks must have at most 30 items")
        List<@Valid DescriptionBlockRequest> descriptionBlocks,

        List<@Valid ItemVariantRequest> variants,

        @Min(value = 0, message = "lowStockDefault must be at least 0")
        Integer lowStockDefault,

        ItemStatus status
) {
}
