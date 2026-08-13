package kh.edu.istad.ite.features.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartSummaryResponse(
        int storeCount,
        int totalItems,
        List<StoreCart> stores
) {

    public record StoreCart(
            UUID cartId,
            UUID businessId,
            String slug,
            String name,
            String category,
            String logo,
            String location,
            String hours,
            String currency,
            boolean open,
            int itemCount,
            BigDecimal subtotal,
            List<Line> items
    ) {
    }

    public record Line(
            UUID cartItemId,
            UUID itemId,
            UUID variantId,
            String name,
            String description,
            String imageUrl,
            /**
             * The chips shown under the line — the option, then each choice.
             * Kept as flat display text so a cart list needs no vocabulary for
             * what it is showing; {@link #selections} is the structured form
             * for anything that does.
             */
            List<String> badges,
            /** The options chosen, by name, for a client that wants them apart. */
            List<Selection> selections,
            /**
             * The unit bought, and how many base units one of them holds. A
             * case of twenty-four must never read as a single can — it is
             * different money and different stock.
             */
            UUID unitId,
            String unitName,
            BigDecimal unitFactor,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }

    public record Selection(
            String attributeName,
            String value,
            String label
    ) {
    }
}