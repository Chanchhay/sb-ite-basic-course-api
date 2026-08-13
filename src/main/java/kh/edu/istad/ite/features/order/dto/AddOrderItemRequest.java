package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AddOrderItemRequest(
        @NotNull UUID itemId,
        UUID variantId,
        /** The unit being sold. Left out, the item's base unit. */
        UUID unitId,
        /** Extras chosen on this line. Each must be on sale for the item. */
        List<UUID> addOnIds,
        @NotNull @Positive Integer quantity,
        BigDecimal discountAmount
) {
}
