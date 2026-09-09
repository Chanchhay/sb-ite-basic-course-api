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
            /** The extras ticked, priced as they stood when they were. */
            List<AddOn> addOns,
            /**
             * The unit bought, and how many base units one of them holds. A
             * case of twenty-four must never read as a single can — it is
             * different money and different stock.
             */
            UUID unitId,
            String unitName,
            BigDecimal unitFactor,
            int quantity,
            /** How many of {@code quantity} a Buy X Get Y offer gave away — zero for an ordinary line. */
            int freeQuantity,
            /** The thing itself, without its extras. */
            BigDecimal unitPrice,
            /**
             * What one of this line is actually billed at — the price above
             * plus every extra ticked on it. This is what {@link #subtotal}
             * is a multiple of, so a cart that shows a per-unit price should
             * show this one.
             */
            BigDecimal unitPriceWithAddOns,
            BigDecimal subtotal,
            /** What this line's units would cost before the active promotion. Null when none applies. */
            BigDecimal compareAtPrice,
            /** Total knocked off this whole line by the promotion — a line total, not per-unit. */
            BigDecimal discountAmount,
            /** The promotion's own name, so the cart names the same discount checkout will apply. */
            String discountLabel
    ) {
    }

    public record Selection(
            String attributeName,
            String value,
            String label
    ) {
    }

    /** One extra riding on a line, at the price it was ticked at. */
    public record AddOn(
            UUID addOnId,
            String name,
            BigDecimal unitPrice
    ) {
    }
}