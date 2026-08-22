package kh.edu.istad.ite.features.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One delivery still on the shelf, and what it cost.
 *
 * Stock is not one number at one price. Each delivery keeps the price it
 * arrived at, and a sale eats the oldest one first — so an item can be sitting
 * on two batches bought months apart at different money, and what the next
 * sale costs depends on which of them it comes out of.
 *
 * This is what makes the margin on a receipt explicable. Without it the shop
 * is told what it made and has to take that on trust.
 */
public record StockBatchResponse(
        UUID id,
        /** The option this batch arrived for. Null on an item with no options. */
        UUID variantId,
        String variantName,
        /** What one base unit of this batch cost. */
        BigDecimal unitCost,
        BigDecimal quantityReceived,
        BigDecimal quantityRemaining,
        /** What is left of this batch is worth, at what it cost. */
        BigDecimal remainingValue,
        LocalDateTime receivedAt,
        /** The supplier's reference for the delivery, for traceability. */
        String lotNumber,
        LocalDate manufacturedAt,
        /** When it goes off. Null on a batch that does not expire. */
        LocalDate expiresAt,
        /**
         * Whether it is already past its date.
         *
         * Worked out here rather than left to the reader, who would have to
         * know which clock to compare against. Expired stock still sells — a
         * till stopped mid-sale by a stale date is worse than a flag — so this
         * is what tells a shop there is something to write off.
         */
        boolean expired,
        /**
         * Where in the queue this batch sits: soonest to expire first, then
         * oldest arrival among those that never expire.
         *
         * The next sale draws from position 1. Spelled out rather than left to
         * the reader to infer from the dates, which is the thing people get
         * wrong — position 1 is not always the oldest delivery.
         */
        int position
) {
}
