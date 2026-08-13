package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ItemVariantRequest(
        @NotBlank(message = "variant name cannot be empty")
        @Size(max = 150, message = "variant name must be at most 150 characters")
        String name,

        @Size(max = 100, message = "variant sku must be at most 100 characters")
        String sku,

        @Size(max = 100, message = "variant barcode must be at most 100 characters")
        String barcode,

        // The option's own picture, already uploaded: the client sends the URL
        // the asset store answered with, never the file.
        @Size(max = 500, message = "variant image URL must be at most 500 characters")
        String imageUrl,

        // The size half of the pair — "Large". Falls back to the variant's own
        // name on an item that is not sold by colour.
        @Size(max = 150, message = "optionName must be at most 150 characters")
        String optionName,

        // Which of the item's colours this row is. Null when the size alone is
        // the whole variant.
        @Size(max = 150, message = "colorValue must be at most 150 characters")
        String colorValue,

        @DecimalMin(value = "0.0", inclusive = true, message = "variant price must be at least zero")
        @Digits(integer = 10, fraction = 2, message = "variant price must have at most 10 integer digits and 2 decimal places")
        BigDecimal price,

        Boolean available
) {
    public ItemVariantRequest {
        if (available == null) {
            available = true;
        }
    }
}
