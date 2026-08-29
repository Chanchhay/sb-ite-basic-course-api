package kh.edu.istad.ite.features.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID itemId,
        UUID variantId,
        /** The option chosen, so a line can say which one it is. */
        String variantName,
        String itemName,
        /** The unit sold, and how many base units one of them holds. */
        UUID unitId,
        String unitName,
        BigDecimal unitFactor,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        /** Name of the discount that produced discountAmount for this line, if any. */
        String discountLabel,
        BigDecimal lineTotal,
        Boolean trackInventory,
        /** Extras chosen on this line, priced as they were when rung up. */
        List<OrderItemAddOnResponse> addOns,
        /**
         * The options chosen on this line — "Sugar Level: 50%". Costs nothing,
         * but it is what the line has to be made as.
         */
        List<OrderItemSelectionResponse> selections
) {
    public record OrderItemAddOnResponse(
            UUID addOnId,
            String name,
            BigDecimal unitPrice
    ) {
    }

    public record OrderItemSelectionResponse(
            String attributeName,
            String value,
            String label
    ) {
    }
}
