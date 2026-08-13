package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the business made, and where it made it.
 *
 * Profit rather than revenue because revenue on its own says nothing about
 * whether a channel is worth running: a channel discounting hard can take the
 * most money and keep the least of it, and the shop has no way of noticing
 * until it is told what each sale cost.
 *
 * Cost is what the stock actually cost, batch by batch, recorded at the moment
 * of each sale. It does not move when the shelf price does.
 */
public record SalesProfitResponse(
        List<ChannelProfit> channels,
        ChannelProfit total
) {

    public record ChannelProfit(
            /** Null on the total row, which is every channel at once. */
            OrderChannel channel,
            long sales,
            long itemsSold,
            /** Before discounts. */
            BigDecimal grossSales,
            BigDecimal discounts,
            /** What was actually taken. */
            BigDecimal revenue,
            BigDecimal cost,
            /** {@code revenue - cost}. Negative when it sold below cost. */
            BigDecimal profit,
            /**
             * Profit as a percentage of revenue, to two places.
             *
             * Null rather than zero when nothing was taken: a channel with no
             * sales has no margin, and printing 0% would read as one that
             * makes nothing on everything it sells.
             */
            BigDecimal marginPercent
    ) {
    }
}
