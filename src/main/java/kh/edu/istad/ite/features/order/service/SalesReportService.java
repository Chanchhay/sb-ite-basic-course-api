package kh.edu.istad.ite.features.order.service;

import kh.edu.istad.ite.features.order.dto.DailyChannelRevenue;
import kh.edu.istad.ite.features.order.dto.ItemProfitResponse;
import kh.edu.istad.ite.features.order.dto.PeriodProfitResponse;
import kh.edu.istad.ite.features.order.dto.SalesProfitResponse;
import kh.edu.istad.ite.features.order.repository.SaleRepository;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.ReportGranularity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the shop made, per channel it sells on.
 *
 * The arithmetic is trivial; the reason it lives here rather than on the
 * screen is that the rows come out of a {@code group by} the caller never
 * sees. A client totalling its own pages can only ever total the pages it
 * asked for, and quietly under-reports the moment a shop has a good year.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    private final SaleRepository saleRepository;

    public SalesProfitResponse profitByChannel(
            UUID businessId,
            LocalDateTime from,
            LocalDateTime to) {

        List<SalesProfitResponse.ChannelProfit> channels = new ArrayList<>();

        long sales = 0;
        long itemsSold = 0;
        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

        for (SaleRepository.ChannelProfitProjection row
                : saleRepository.profitByChannel(businessId, from, to)) {

            channels.add(toChannelProfit(
                    row.getChannel(),
                    row.getSales(),
                    row.getItemsSold(),
                    row.getGrossSales(),
                    row.getDiscounts(),
                    row.getRevenue(),
                    row.getCost()));

            // Totalled from the same rows rather than by a second query, so
            // the total can never disagree with the lines above it.
            sales += row.getSales();
            itemsSold += row.getItemsSold();
            grossSales = grossSales.add(orZero(row.getGrossSales()));
            discounts = discounts.add(orZero(row.getDiscounts()));
            revenue = revenue.add(orZero(row.getRevenue()));
            cost = cost.add(orZero(row.getCost()));
        }

        return new SalesProfitResponse(
                channels,
                toChannelProfit(null, sales, itemsSold, grossSales, discounts, revenue, cost));
    }

    private static SalesProfitResponse.ChannelProfit toChannelProfit(
            kh.edu.istad.ite.shared.enums.OrderChannel channel,
            long sales,
            long itemsSold,
            BigDecimal grossSales,
            BigDecimal discounts,
            BigDecimal revenue,
            BigDecimal cost) {

        BigDecimal takings = orZero(revenue);
        BigDecimal spent = orZero(cost);
        BigDecimal profit = takings.subtract(spent);

        return new SalesProfitResponse.ChannelProfit(
                channel,
                sales,
                itemsSold,
                money(grossSales),
                money(discounts),
                money(takings),
                money(spent),
                money(profit),
                // Nothing taken is no margin, not a margin of nothing.
                takings.signum() == 0
                        ? null
                        : profit.multiply(BigDecimal.valueOf(100))
                                .divide(takings, 2, RoundingMode.HALF_UP));
    }

    /**
     * The same sales, sliced into calendar periods.
     *
     * The total is accumulated from the rows as they are read rather than
     * asked for separately: a second query can disagree with the lines above
     * it the moment a sale lands between the two, and a statement whose total
     * does not match its own rows is worse than one that is merely stale.
     */
    public PeriodProfitResponse profitByPeriod(
            UUID businessId,
            LocalDateTime from,
            LocalDateTime to,
            ReportGranularity granularity) {

        List<PeriodProfitResponse.PeriodProfit> periods = new ArrayList<>();

        long sales = 0;
        long itemsSold = 0;
        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

        for (SaleRepository.PeriodProfitProjection row
                : saleRepository.profitByPeriod(
                        businessId, from, to, granularity.truncField())) {

            periods.add(toPeriodProfit(
                    LocalDate.parse(row.getPeriodStart()),
                    row.getSales(),
                    row.getItemsSold(),
                    row.getGrossSales(),
                    row.getDiscounts(),
                    row.getTax(),
                    row.getRevenue(),
                    row.getCost()));

            sales += row.getSales();
            itemsSold += row.getItemsSold();
            grossSales = grossSales.add(orZero(row.getGrossSales()));
            discounts = discounts.add(orZero(row.getDiscounts()));
            tax = tax.add(orZero(row.getTax()));
            revenue = revenue.add(orZero(row.getRevenue()));
            cost = cost.add(orZero(row.getCost()));
        }

        return new PeriodProfitResponse(
                granularity,
                periods,
                // No period of its own: this row is all of them at once.
                toPeriodProfit(
                        null, sales, itemsSold, grossSales, discounts, tax, revenue, cost));
    }

    private static PeriodProfitResponse.PeriodProfit toPeriodProfit(
            LocalDate periodStart,
            long sales,
            long itemsSold,
            BigDecimal grossSales,
            BigDecimal discounts,
            BigDecimal tax,
            BigDecimal revenue,
            BigDecimal cost) {

        BigDecimal takings = orZero(revenue);
        BigDecimal spent = orZero(cost);
        BigDecimal profit = takings.subtract(spent);

        return new PeriodProfitResponse.PeriodProfit(
                periodStart,
                sales,
                itemsSold,
                money(grossSales),
                money(discounts),
                money(tax),
                money(takings),
                money(spent),
                money(profit),
                // Nothing taken is no margin, not a margin of nothing.
                takings.signum() == 0
                        ? null
                        : profit.multiply(BigDecimal.valueOf(100))
                                .divide(takings, 2, RoundingMode.HALF_UP));
    }

    /**
     * The same sales, broken down by what was actually sold.
     *
     * Totalled from the rows as they are read, for the same reason the
     * statement is: a separate total query can disagree with the lines above
     * it the moment a sale lands between the two.
     */
    public ItemProfitResponse profitByItem(
            UUID businessId,
            LocalDateTime from,
            LocalDateTime to) {

        List<ItemProfitResponse.ItemProfit> items = new ArrayList<>();

        long quantitySold = 0;
        long lines = 0;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

        for (SaleRepository.ItemProfitProjection row
                : saleRepository.profitByItem(businessId, from, to)) {

            items.add(toItemProfit(
                    row.getItemId(),
                    row.getVariantId(),
                    row.getItemName(),
                    row.getVariantName(),
                    row.getQuantitySold(),
                    row.getLines(),
                    row.getDiscounts(),
                    row.getRevenue(),
                    row.getCost()));

            quantitySold += row.getQuantitySold();
            lines += row.getLines();
            discounts = discounts.add(orZero(row.getDiscounts()));
            revenue = revenue.add(orZero(row.getRevenue()));
            cost = cost.add(orZero(row.getCost()));
        }

        return new ItemProfitResponse(
                items,
                // No item of its own: this row is all of them at once.
                toItemProfit(
                        null, null, null, null,
                        quantitySold, lines, discounts, revenue, cost));
    }

    private static ItemProfitResponse.ItemProfit toItemProfit(
            String itemId,
            String variantId,
            String itemName,
            String variantName,
            long quantitySold,
            long lines,
            BigDecimal discounts,
            BigDecimal revenue,
            BigDecimal cost) {

        BigDecimal takings = orZero(revenue);
        BigDecimal spent = orZero(cost);
        BigDecimal profit = takings.subtract(spent);

        return new ItemProfitResponse.ItemProfit(
                itemId,
                variantId,
                itemName,
                variantName,
                quantitySold,
                lines,
                money(discounts),
                money(takings),
                money(spent),
                money(profit),
                // Nothing taken is no margin, not a margin of nothing.
                takings.signum() == 0
                        ? null
                        : profit.multiply(BigDecimal.valueOf(100))
                                .divide(takings, 2, RoundingMode.HALF_UP));
    }

    public List<DailyChannelRevenue> dailyRevenueByChannel(
            UUID businessId,
            LocalDateTime from,
            LocalDateTime to) {

        return saleRepository.dailyRevenueByChannel(businessId, from, to).stream()
                .map(row -> new DailyChannelRevenue(
                        LocalDate.parse(row.getDay()),
                        OrderChannel.valueOf(row.getChannel()),
                        money(row.getRevenue())))
                .toList();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return orZero(value).setScale(2, RoundingMode.HALF_UP);
    }
}
