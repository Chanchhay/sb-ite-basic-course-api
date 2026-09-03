package kh.edu.istad.ite.features.dashboard.dto;

import kh.edu.istad.ite.shared.enums.ReportGranularity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the dashboard's cards draw, worked out here rather than there.
 *
 * The screen used to fetch four separate reports plus the whole catalogue —
 * up to ten thousand items — and derive its headline figures, its percentages
 * and its running totals in the browser on every visit. Two problems with
 * that: the arithmetic ran again on every render for every viewer, and the
 * catalogue had to cross the wire in full to produce four numbers nobody
 * could read off it directly.
 *
 * Each field below is final. Nothing here needs summing, sorting, ranking or
 * accumulating before it can be shown.
 *
 * What deliberately stays on the screen is presentation and nothing else:
 * which colour a channel is drawn in, how a long name is elided, what a
 * currency looks like once formatted. Those are the client's business and
 * change with the theme, not with the data.
 */
public record DashboardOverviewResponse(
        Kpis kpis,
        List<ChannelShare> channels,
        ProfitTrend profitTrend,
        List<TopItem> topItems,
        List<StockLevel> stockLevels
) {

    /** The headline figures, each already reduced to the single number shown. */
    public record Kpis(
            /** Takings across every channel, for the range asked for. */
            BigDecimal revenue,
            /** How many items the catalogue holds. */
            long totalItems,
            /** How many distinct categories those items actually sit in. */
            long totalCategories,
            /** Every unit on the shelf, added up. */
            BigDecimal inventoryOnHand
    ) {
    }

    /**
     * One channel's share of revenue.
     *
     * The percentage is computed against the same total the other rows are,
     * so the slices always add up — a client dividing each row by a total it
     * fetched separately cannot promise that.
     */
    public record ChannelShare(
            String channel,
            /** Whole percent of total revenue, floored at 1 so a real channel is never invisible. */
            int percentage,
            BigDecimal revenue
    ) {
    }

    /** Profit accumulating over time, already in the order it is drawn. */
    public record ProfitTrend(
            ReportGranularity granularity,
            /** Oldest first. A running total read backwards is not a running total. */
            List<ProfitPoint> points
    ) {
    }

    public record ProfitPoint(
            LocalDate periodStart,
            /** Named for the granularity asked for — "Mon 3", "Aug", "2026". */
            String label,
            /** What this period alone made. */
            BigDecimal profit,
            /** What every period up to and including this one made. */
            BigDecimal cumulative
    ) {
    }

    /** One bar on the item comparison: what it earned beside how many sold. */
    public record TopItem(
            String itemId,
            String name,
            long itemCount,
            BigDecimal totalAmount
    ) {
    }

    /**
     * One row of the stock chart.
     *
     * The two percentages are each row's share of the largest row, which is
     * what the bars are drawn to. They are here because they depend on the
     * whole ranked set — a row cannot work out its own bar width alone, and a
     * client that ranks a page of rows would scale them against the wrong
     * maximum.
     */
    public record StockLevel(
            String itemId,
            String name,
            BigDecimal quantityOnHand,
            BigDecimal totalAmount,
            int revenuePercent,
            int countPercent
    ) {
    }
}
