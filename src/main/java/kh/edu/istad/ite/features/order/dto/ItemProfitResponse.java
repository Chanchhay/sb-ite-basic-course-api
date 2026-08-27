package kh.edu.istad.ite.features.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What each item sold, and what the shop kept on it.
 *
 * The statement says a month made money; this says which items made it. A shop
 * with one strong line carrying three weak ones cannot see that from a period
 * total, and will keep restocking all four.
 *
 * Revenue here is the sum of the sale lines: unit price times quantity, less
 * whatever was discounted off that line, add-ons included. It is deliberately
 * not the statement's revenue — a discount applied to a whole order belongs to
 * no single line, so the two agree only when every discount was given at the
 * line. The statement remains the figure the books are kept on.
 */
public record ItemProfitResponse(
        List<ItemProfit> items,
        /** Every item at once, totalled from the same rows shown above. */
        ItemProfit total
) {

    public record ItemProfit(
            /** Null on the total row, which belongs to no single item. */
            String itemId,
            /** The option sold, when the item is sold in options. */
            String variantId,
            /** As it was called on the receipt, so a renamed item still reads. */
            String itemName,
            String variantName,
            long quantitySold,
            /** How many separate sale lines it appeared on. */
            long lines,
            BigDecimal discounts,
            /** Sale lines, after line discounts. */
            BigDecimal revenue,
            /** What the stock cost, batch by batch, as recorded at each sale. */
            BigDecimal cost,
            /** {@code revenue - cost}. Negative when it sold below cost. */
            BigDecimal profit,
            /** Null when nothing was taken — no margin, not a margin of zero. */
            BigDecimal marginPercent
    ) {
    }
}
