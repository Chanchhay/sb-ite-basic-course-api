package kh.edu.istad.ite.features.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One batch a movement drew from, and what that part of it cost.
 *
 * A sale of six often spans two deliveries — the last five of the old batch at
 * one price and one of the new at another. The movement records a single cost
 * for the whole thing, and that number is not explicable from the outside: it
 * is neither of the prices paid, and dividing it by the quantity gives an
 * average that matches no batch on the shelf.
 *
 * This is the working behind it. Without it a shop is told what its margin was
 * and has to take that on trust.
 */
public record StockConsumptionResponse(
        /** The batch drawn from, so it can be tied to the batches on hand. */
        UUID batchId,
        /** Its lot and dates, carried so the row reads as the delivery it was. */
        String lotNumber,
        LocalDate expiresAt,
        LocalDateTime receivedAt,
        /** How much this movement took from that batch. */
        BigDecimal quantity,
        /** What one unit of it cost — the price frozen when it was consumed. */
        BigDecimal unitCost,
        /** What this share of the movement cost: quantity times unit cost. */
        BigDecimal cost
) {
}
