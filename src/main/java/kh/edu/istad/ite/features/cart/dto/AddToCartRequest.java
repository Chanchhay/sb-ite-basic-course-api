package kh.edu.istad.ite.features.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AddToCartRequest(
        @NotNull UUID businessId,
        @NotNull UUID itemId,
        UUID variantId,
        /**
         * The unit being bought — a six-pack, a case. Left out, the line is
         * sold as one of the item's base unit, which is how everything sold
         * before items could be bought by the pack.
         */
        UUID unitId,
        /**
         * The options chosen on this line. Absent is the same as none, so a
         * client that does not offer options keeps working unchanged.
         */
        @Valid List<CartSelectionRequest> selections,
        @NotNull @Min(1) Integer quantity
) {
}
