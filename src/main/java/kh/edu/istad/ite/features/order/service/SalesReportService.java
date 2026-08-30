package kh.edu.istad.ite.features.order.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.order.dto.CollectPayLaterRequest;
import kh.edu.istad.ite.features.order.dto.DailyChannelRevenue;
import kh.edu.istad.ite.features.order.dto.SaleResponse;
import kh.edu.istad.ite.features.order.dto.ItemProfitResponse;
import kh.edu.istad.ite.features.order.dto.PeriodProfitResponse;
import kh.edu.istad.ite.features.order.dto.SalesProfitResponse;
import kh.edu.istad.ite.features.order.dto.PredictionItem;
import kh.edu.istad.ite.features.order.dto.PredictionWindow;
import kh.edu.istad.ite.features.order.dto.SalesPredictionsResponse;
import kh.edu.istad.ite.features.order.entity.Sale;
import kh.edu.istad.ite.features.order.mapper.OrderMapper;
import kh.edu.istad.ite.features.order.repository.SaleRepository;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.enums.ReportGranularity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private static final String CURRENCY_KHR = "KHR";

    private final SaleRepository saleRepository;
    private final BusinessHelper businessHelper;
    private final OrderMapper orderMapper;
    private final ItemRepository itemRepository;
    private final StockEntryService stockEntryService;

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

    /** Every sale rung up as "pay later" that hasn't been collected yet. */
    public List<SaleResponse> payLaterSales(UUID businessId) {
        businessHelper.findAccessibleBusiness(businessId);

        return saleRepository.findUnsettledByBusinessId(businessId).stream()
                .map(orderMapper::toSaleResponse)
                .toList();
    }

    /** Settles a pay-later sale once the money actually comes in. */
    @Transactional
    public SaleResponse collectPayLater(UUID businessId, UUID saleId, CollectPayLaterRequest request) {
        businessHelper.findAccessibleBusiness(businessId);

        Sale sale = saleRepository.findById(saleId)
                .filter(s -> s.getBusiness().getId().equals(businessId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale has not been found"));

        if (sale.getPaidAmount().compareTo(sale.getTotalAmount()) >= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This sale has already been settled");
        }

        if (kh.edu.istad.ite.shared.enums.PaymentMethodType.PAY_LATER.equals(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Collecting a sale requires an actual payment method");
        }

        int scale = CURRENCY_KHR.equalsIgnoreCase(sale.getCurrency()) ? 0 : 2;
        BigDecimal owed = sale.getTotalAmount().subtract(sale.getPaidAmount());
        BigDecimal received = request.receivedAmount() == null
                ? owed
                : request.receivedAmount().setScale(scale, RoundingMode.HALF_UP);

        if (received.compareTo(owed) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Received " + received + " is less than the amount owed " + owed);
        }

        sale.setPaidAmount(sale.getTotalAmount());
        sale.setChangeAmount(received.subtract(owed).setScale(scale, RoundingMode.HALF_UP));
        sale.setPaymentMethod(request.paymentMethod());

        return orderMapper.toSaleResponse(saleRepository.save(sale));
    }

    public SalesPredictionsResponse getSalesPredictions(UUID businessId, PredictionWindow window) {
        businessHelper.findOwnedBusiness(businessId);
        PredictionWindow activeWindow = window != null ? window : PredictionWindow.WEEK;
        int windowDays = activeWindow == PredictionWindow.MONTH ? 30 : 7;
        int prevWindowDays = windowDays * 2;
        int maxDays = Math.max(prevWindowDays, 30);

        LocalDateTime now = LocalDateTime.now();

        List<SaleRepository.ItemPredictionBucketProjection> buckets =
                saleRepository.findItemPredictionBuckets(businessId, now, windowDays, prevWindowDays, maxDays);

        Map<UUID, SaleRepository.ItemPredictionBucketProjection> bucketByItemId = buckets.stream()
                .filter(b -> b.getItemId() != null)
                .map(b -> Map.entry(tryParseUuid(b.getItemId()), b))
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing
                ));

        List<StockSummaryResponse> stockSummaries = stockEntryService.findCurrentStock(businessId);
        Map<UUID, BigDecimal> stockByItemId = new HashMap<>();
        for (StockSummaryResponse summary : stockSummaries) {
            if (summary.itemId() != null) {
                BigDecimal current = stockByItemId.getOrDefault(summary.itemId(), BigDecimal.ZERO);
                BigDecimal onHand = summary.quantityOnHand() == null ? BigDecimal.ZERO : summary.quantityOnHand();
                stockByItemId.put(summary.itemId(), current.add(onHand));
            }
        }

        List<Item> allItems = itemRepository.findAllByBusinessIdOrderByNameAsc(businessId);

        List<PredictionItem> predictionItems = new ArrayList<>();
        long risingCount = 0;
        long stockoutSoonCount = 0;
        long slowMoverCount = 0;

        for (Item item : allItems) {
            UUID itemId = item.getId();
            String name = item.getName();

            BigDecimal currentStock = stockByItemId.getOrDefault(itemId, BigDecimal.ZERO);
            SaleRepository.ItemPredictionBucketProjection bucket = bucketByItemId.get(itemId);

            long windowQty = bucket != null ? bucket.getWindowQty() : 0L;
            long prevWindowQty = bucket != null ? bucket.getPrevWindowQty() : 0L;
            long last30Qty = bucket != null ? bucket.getLast30Qty() : 0L;

            double avgDailyDemand = roundTo2Decimals(windowQty / (double) windowDays);
            long expectedDemandWindow = windowQty;

            Double trendPercent = null;
            if (prevWindowQty > 0) {
                trendPercent = roundTo1Decimal(((double) (windowQty - prevWindowQty) / prevWindowQty) * 100.0);
            }

            double safetyStock = Math.ceil(avgDailyDemand * 2.0);
            long recommendedRestock = Math.max(0L, (long) Math.ceil(expectedDemandWindow + safetyStock - currentStock.doubleValue()));

            Double estimatedStockoutDays = null;
            if (avgDailyDemand > 0) {
                estimatedStockoutDays = roundTo1Decimal(currentStock.doubleValue() / avgDailyDemand);
            }

            if (trendPercent != null && trendPercent >= 10.0) {
                risingCount++;
            }
            if (estimatedStockoutDays != null && estimatedStockoutDays <= windowDays) {
                stockoutSoonCount++;
            }
            if (currentStock.compareTo(BigDecimal.ZERO) > 0 && last30Qty <= 3) {
                slowMoverCount++;
            }

            predictionItems.add(new PredictionItem(
                    itemId,
                    name,
                    currentStock,
                    avgDailyDemand,
                    expectedDemandWindow,
                    trendPercent,
                    estimatedStockoutDays,
                    recommendedRestock,
                    last30Qty
            ));
        }

        SaleRepository.BusinessRevenueTrendProjection revenueTrendProj =
                saleRepository.findBusinessRevenueTrend(businessId, now, windowDays, prevWindowDays);

        BigDecimal windowRevenue = revenueTrendProj != null && revenueTrendProj.getWindowRevenue() != null
                ? revenueTrendProj.getWindowRevenue() : BigDecimal.ZERO;
        BigDecimal prevWindowRevenue = revenueTrendProj != null && revenueTrendProj.getPrevWindowRevenue() != null
                ? revenueTrendProj.getPrevWindowRevenue() : BigDecimal.ZERO;

        double revenueTrend = 0.0;
        if (prevWindowRevenue.compareTo(BigDecimal.ZERO) > 0) {
            double rawTrend = windowRevenue.subtract(prevWindowRevenue)
                    .divide(prevWindowRevenue, 4, RoundingMode.HALF_UP).doubleValue();
            revenueTrend = Math.max(-0.5, Math.min(0.5, rawTrend));
        }

        BigDecimal mid = windowRevenue.multiply(BigDecimal.valueOf(1.0 + revenueTrend)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal low = mid.multiply(BigDecimal.valueOf(0.9)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal high = mid.multiply(BigDecimal.valueOf(1.1)).setScale(2, RoundingMode.HALF_UP);

        SalesPredictionsResponse.RevenueForecast revenueForecast =
                new SalesPredictionsResponse.RevenueForecast(low, mid, high);

        return new SalesPredictionsResponse(
                now,
                windowDays,
                risingCount,
                stockoutSoonCount,
                slowMoverCount,
                revenueForecast,
                predictionItems
        );
    }

    private static double roundTo1Decimal(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private static double roundTo2Decimals(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return orZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static UUID tryParseUuid(String uuidStr) {
        if (uuidStr == null) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
