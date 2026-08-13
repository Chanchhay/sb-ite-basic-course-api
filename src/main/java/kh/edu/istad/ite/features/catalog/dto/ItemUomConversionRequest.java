package kh.edu.istad.ite.features.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemUomConversionRequest(
        @NotNull(message = "conversion unitId cannot be null")
        UUID unitId,

        /**
         * Which option it is for, by id. Only a saved option has one.
         */
        UUID variantId,

        /**
         * Which option it is for, by name.
         *
         * Options and conversions are set up on the same screen and saved
         * together, so a case can be declared for an option typed a moment ago
         * that has no id yet. The name is what the two halves of the request
         * have in common, and it is unique within the item. Required on an
         * item sold in options: a case of Large is not a case of Small.
         */
        String variantName,

        @NotNull(message = "conversion factor cannot be null")
        @DecimalMin(value = "0.0", inclusive = false, message = "conversion factor must be greater than zero")
        @Digits(integer = 12, fraction = 6, message = "conversion factor must have at most 12 integer digits and 6 decimal places")
        BigDecimal factor,

        /**
         * What one of this unit sells for. Left out, the item is bought and
         * counted in this unit but never sold in it.
         */
        @DecimalMin(value = "0.0", message = "conversion price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "conversion price must have at most 10 integer digits and 2 decimal places")
        BigDecimal price
) {
}
