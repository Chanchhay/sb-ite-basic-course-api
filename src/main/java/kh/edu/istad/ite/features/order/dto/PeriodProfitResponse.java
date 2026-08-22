package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.shared.enums.ReportGranularity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the business made, period by period.
 *
 * The accounting view of the same sales the channel report totals: each row is
 * one day, week, month or year, and the columns are the line items a set of
 * books is kept in.
 *
 * Revenue here is net of tax, and that is deliberate. Tax collected is money
 * held for the tax authority, never the shop's to keep, so counting it as
 * revenue would inflate both the profit and the margin by whatever the shop
 * happens to charge. It is reported in its own column instead, where it can be
 * read as what it is: a liability, and the number a return is filed on.
 */
public record PeriodProfitResponse(
        ReportGranularity granularity,
        List<PeriodProfit> periods,
        /** Every period at once, totalled from the same rows shown above. */
        PeriodProfit total
) {

    public record PeriodProfit(
            /**
             * The first day of the period.
             *
             * A date rather than a label, so the screen can name it the way
             * the granularity calls for — "Aug 2026" for a month, the week
             * commencing for a week — without parsing anything back.
             *
             * Null on the total row, which belongs to no single period.
             */
            LocalDate periodStart,
            long sales,
            long itemsSold,
            /** Before discounts. */
            BigDecimal grossSales,
            BigDecimal discounts,
            /** Charged to the customer and owed onward. Never part of profit. */
            BigDecimal tax,
            /** What was taken and kept, with tax taken back out. */
            BigDecimal revenue,
            /** What the stock cost, batch by batch, as recorded at each sale. */
            BigDecimal cost,
            /** {@code revenue - cost}. Negative when it sold below cost. */
            BigDecimal profit,
            /**
             * Profit as a percentage of revenue, to two places.
             *
             * Null rather than zero when nothing was taken: a period with no
             * sales has no margin, and 0% would read as one that made nothing
             * on everything it sold.
             */
            BigDecimal marginPercent
    ) {
    }
}
