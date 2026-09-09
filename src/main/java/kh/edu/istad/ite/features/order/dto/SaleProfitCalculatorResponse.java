package kh.edu.istad.ite.features.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The same experiment, priced: every catalog item at the requested margin,
 * and the shop-wide totals that margin adds up to.
 */
public record SaleProfitCalculatorResponse(
        SaleProfitCalculatorMode mode,
        BigDecimal operatingExpense,
        /** Echoed back only in BUSINESS_TARGET mode. */
        BigDecimal targetMarginPercent,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal grossProfit,
        BigDecimal grossMarginPercent,
        BigDecimal netProfit,
        BigDecimal netMarginPercent,
        List<CalculatorItem> items
) {
    public record CalculatorItem(
            UUID itemId,
            String name,
            /** Weighted average of what's still on the shelf, not the last delivery price. */
            BigDecimal cost,
            BigDecimal qty,
            BigDecimal marginPercent,
            /** Null when the margin is 100% or more — no price can reach it. */
            BigDecimal price,
            BigDecimal revenue,
            BigDecimal profit,
            /** Set only in BUSINESS_TARGET mode: this item's price scaled to hit the target. */
            BigDecimal newPrice,
            BigDecimal newMarginPercent
    ) {
    }
}
