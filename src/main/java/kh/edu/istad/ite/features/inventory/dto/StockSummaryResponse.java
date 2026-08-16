package kh.edu.istad.ite.features.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record StockSummaryResponse(
        /** One of the two is set, matching the entries behind the summary. */
        UUID itemId,
        UUID addOnId,
        /**
         * Which option this balance is for. An item sold in options has one
         * summary per option; null alongside an itemId is the item as a whole,
         * which is either an item with no options or stock recorded before it
         * had any.
         */
        UUID variantId,
        String variantName,
        BigDecimal quantityOnHand,
        /**
         * What is left is worth, from the batches still holding it.
         *
         * Every delivery keeps the price it arrived at, so this is the sum of
         * what each open batch still holds times what that batch cost. A
         * single cost per item cannot stand in for it once two deliveries at
         * different prices are both on the shelf.
         */
        BigDecimal stockValue,
        /** What the next unit out will cost: the oldest batch still holding any. */
        BigDecimal unitCost,
        UUID lastEntryId,
        LocalDateTime updatedAt
) {
}
