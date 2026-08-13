package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;


public record CreateOrderItemRequest(
        @NotNull
        UUID itemId,

        UUID variantId,

        /**
         * The unit being sold — a case, a six-pack. Left out, the line is sold
         * in the item's base unit, which is how everything sold before items
         * could be sold by the pack.
         */
        UUID unitId,

        /** Extras chosen on this line. Each must be on sale for the item. */
        List<UUID> addOnIds,

        @NotNull
        @Positive
        Integer quantity
) {
}
