package kh.edu.istad.ite.features.dashboard.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.dashboard.dto.BestSellingRow;
import kh.edu.istad.ite.features.dashboard.dto.DashboardOverviewResponse;
import kh.edu.istad.ite.features.dashboard.dto.RecentOrderRow;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.service.StockEntryService;
import kh.edu.istad.ite.features.order.dto.ItemProfitResponse;
import kh.edu.istad.ite.features.order.dto.PeriodProfitResponse;
import kh.edu.istad.ite.features.order.dto.SalesProfitResponse;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.order.service.SalesReportService;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import kh.edu.istad.ite.shared.enums.ReportGranularity;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The dashboard, worked out in one place.
 *
 * Every figure this returns was previously derived in the browser from raw
 * feeds — four separate reports plus the entire catalogue, up to ten thousand
 * items, fetched so that four headline numbers could be counted off it. The
 * arithmetic is not hard; what makes it belong here is that most of it needs
 * the whole set to be correct. A running total, a percentage of revenue, a
 * ranking, a bar scaled to the largest row: each is wrong the moment it is
 * computed over a page rather than over everything.
 *
 * The reports themselves are not reimplemented — {@link SalesReportService}
 * already answers them from a {@code group by}, and this reads those answers
 * and finishes the job.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** How many bars the item comparison chart draws. */
    private static final int TOP_ITEM_COUNT = 6;

    /** How many rows the stock chart draws. */
    private static final int STOCK_LEVEL_COUNT = 5;

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final SalesReportService salesReportService;
    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final StockEntryService stockEntryService;
    private final BusinessHelper businessHelper;

    /**
     * Everything the four cards draw, in one call.
     *
     * One request rather than four because they share their inputs: the same
     * channel report feeds both the revenue headline and the share-by-channel
     * slices, and splitting them would fetch it twice.
     */
    public DashboardOverviewResponse overview(
            UUID businessId,
            LocalDateTime from,
            LocalDateTime to,
            ReportGranularity granularity
    ) {
        businessHelper.findAccessibleBusiness(businessId);

        SalesProfitResponse channelReport = salesReportService.profitByChannel(businessId, from, to);
        PeriodProfitResponse periodReport = salesReportService.profitByPeriod(businessId, from, to, granularity);
        ItemProfitResponse itemReport = salesReportService.profitByItem(businessId, from, to);
        List<StockSummaryResponse> stock = stockEntryService.findCurrentStock(businessId);
        List<Item> catalogue = itemRepository.findAllByBusinessIdOrderByNameAsc(businessId);

        return new DashboardOverviewResponse(
                kpis(channelReport, catalogue, stock),
                channelShares(channelReport),
                profitTrend(periodReport, granularity),
                topItems(itemReport),
                stockLevels(catalogue, stock)
        );
    }

    /* -------------------------------- cards -------------------------------- */

    private DashboardOverviewResponse.Kpis kpis(
            SalesProfitResponse channelReport,
            List<Item> catalogue,
            List<StockSummaryResponse> stock
    ) {
        BigDecimal revenue = channelReport.total() == null
                ? BigDecimal.ZERO
                : orZero(channelReport.total().revenue());

        // Categories actually in use, not categories defined. A shop with
        // twenty empty groups and two stocked ones has two.
        Set<UUID> categories = new LinkedHashSet<>();
        for (Item item : catalogue) {
            if (item.getItemGroup() != null) {
                categories.add(item.getItemGroup().getId());
            }
        }

        BigDecimal onHand = stock.stream()
                .map(summary -> orZero(summary.quantityOnHand()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardOverviewResponse.Kpis(
                revenue,
                catalogue.size(),
                categories.size(),
                onHand);
    }

    private List<DashboardOverviewResponse.ChannelShare> channelShares(SalesProfitResponse report) {
        List<SalesProfitResponse.ChannelProfit> channels = report.channels();
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }

        BigDecimal totalRevenue = channels.stream()
                .map(channel -> orZero(channel.revenue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRevenue.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        List<DashboardOverviewResponse.ChannelShare> shares = new ArrayList<>();
        for (SalesProfitResponse.ChannelProfit channel : channels) {
            BigDecimal revenue = orZero(channel.revenue());
            int percentage = revenue
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalRevenue, 0, RoundingMode.HALF_UP)
                    .intValue();

            shares.add(new DashboardOverviewResponse.ChannelShare(
                    channel.channel() == null ? "OTHER" : channel.channel().name(),
                    // A channel that sold something but rounds to nothing is
                    // still a channel; a zero slice would vanish from the ring.
                    Math.max(percentage, 1),
                    revenue));
        }
        return shares;
    }

    private DashboardOverviewResponse.ProfitTrend profitTrend(
            PeriodProfitResponse report,
            ReportGranularity granularity
    ) {
        List<PeriodProfitResponse.PeriodProfit> periods = report.periods();
        if (periods == null || periods.isEmpty()) {
            return new DashboardOverviewResponse.ProfitTrend(granularity, List.of());
        }

        // The report answers newest-first. A running total only means
        // anything walking forward, and the chart draws left to right.
        List<PeriodProfitResponse.PeriodProfit> chronological = periods.stream()
                .filter(period -> period.periodStart() != null)
                .sorted(Comparator.comparing(PeriodProfitResponse.PeriodProfit::periodStart))
                .toList();

        List<DashboardOverviewResponse.ProfitPoint> points = new ArrayList<>(chronological.size());
        BigDecimal running = BigDecimal.ZERO;

        for (PeriodProfitResponse.PeriodProfit period : chronological) {
            BigDecimal profit = orZero(period.profit());
            running = running.add(profit);
            points.add(new DashboardOverviewResponse.ProfitPoint(
                    period.periodStart(),
                    labelFor(period.periodStart(), granularity),
                    profit,
                    running));
        }

        return new DashboardOverviewResponse.ProfitTrend(granularity, points);
    }

    /** How a period reads on an axis, given how finely the range was cut. */
    private String labelFor(LocalDate periodStart, ReportGranularity granularity) {
        if (periodStart == null) {
            return "";
        }
        return switch (granularity) {
            case DAY, WEEK -> periodStart.format(DAY_LABEL);
            case MONTH -> periodStart.format(MONTH_LABEL);
            case YEAR -> String.valueOf(periodStart.getYear());
        };
    }

    private List<DashboardOverviewResponse.TopItem> topItems(ItemProfitResponse report) {
        List<ItemProfitResponse.ItemProfit> items = report.items();
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        // Ranked across every item that sold, then cut — ranking a page would
        // rank whatever the page happened to hold.
        return items.stream()
                .filter(item -> item.itemId() != null)
                .sorted(Comparator.comparing(
                        (ItemProfitResponse.ItemProfit item) -> orZero(item.revenue())).reversed())
                .limit(TOP_ITEM_COUNT)
                .map(item -> new DashboardOverviewResponse.TopItem(
                        item.itemId(),
                        nameOf(item),
                        item.quantitySold(),
                        orZero(item.revenue()).setScale(0, RoundingMode.HALF_UP)))
                .toList();
    }

    private String nameOf(ItemProfitResponse.ItemProfit item) {
        if (StringUtils.hasText(item.itemName())) return item.itemName();
        if (StringUtils.hasText(item.variantName())) return item.variantName();
        return "Item";
    }

    private List<DashboardOverviewResponse.StockLevel> stockLevels(
            List<Item> catalogue,
            List<StockSummaryResponse> stock
    ) {
        Map<UUID, BigDecimal> onHandByItem = new HashMap<>();
        for (StockSummaryResponse summary : stock) {
            if (summary.itemId() == null) continue;
            onHandByItem.merge(summary.itemId(), orZero(summary.quantityOnHand()), BigDecimal::add);
        }

        record Level(UUID id, String name, BigDecimal quantity, BigDecimal value) {
        }

        List<Level> ranked = catalogue.stream()
                .map(item -> {
                    BigDecimal quantity = onHandByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
                    BigDecimal price = orZero(item.getPrice());
                    return new Level(
                            item.getId(),
                            StringUtils.hasText(item.getName()) ? item.getName() : "Unnamed Item",
                            quantity,
                            quantity.multiply(price).setScale(0, RoundingMode.HALF_UP));
                })
                .filter(level -> level.quantity().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(Level::quantity).reversed())
                .limit(STOCK_LEVEL_COUNT)
                .toList();

        // Both bars are drawn as a share of the biggest row, so the maximum
        // has to be taken over the ranked set rather than per row.
        BigDecimal maxValue = ranked.stream().map(Level::value).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxQuantity = ranked.stream().map(Level::quantity).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        return ranked.stream()
                .map(level -> new DashboardOverviewResponse.StockLevel(
                        level.id().toString(),
                        level.name(),
                        level.quantity(),
                        level.value(),
                        percentOf(level.value(), maxValue),
                        percentOf(level.quantity(), maxQuantity)))
                .toList();
    }

    private int percentOf(BigDecimal value, BigDecimal max) {
        if (max.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return value.multiply(BigDecimal.valueOf(100))
                .divide(max, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /* -------------------------------- tables ------------------------------- */

    /**
     * The recent orders table, filtered and paged by the database.
     *
     * Both the filter and the page are one query, so a search reaches the
     * whole history rather than whatever window happened to be read first,
     * and the total it reports is the real number of matches.
     */
    public PageResponse<RecentOrderRow> recentOrders(UUID businessId, String search, Pageable pageable) {
        businessHelper.findAccessibleBusiness(businessId);

        String needle = StringUtils.hasText(search)
                ? "%" + search.trim().toLowerCase(Locale.ROOT) + "%"
                : null;

        // The table names statuses its own way, so the word typed is matched
        // against those labels and translated back to what is stored.
        Set<OrderStatus> statuses = statusesMatching(search);

        Page<Order> page = orderRepository.searchForDashboard(
                businessId,
                needle,
                !statuses.isEmpty(),
                // An empty collection would make `in ()` invalid, so a
                // placeholder rides along whenever the flag above is false.
                statuses.isEmpty() ? Set.of(OrderStatus.PENDING) : statuses,
                withNewestFirst(pageable));

        return PageResponse.from(page.map(this::toRecentOrderRow));
    }

    /** Newest first, unless the caller has asked for some other order. */
    private Pageable withNewestFirst(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdDate"));
    }

    /**
     * The stored statuses whose label the search term reads like.
     *
     * Typing "paid" has to find PAID, and "success" has to find CONFIRMED,
     * because those are the words the table puts on screen — a shopper of the
     * table searches for what they can see, not for the enum behind it.
     */
    private Set<OrderStatus> statusesMatching(String search) {
        if (!StringUtils.hasText(search)) {
            return Set.of();
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        Set<OrderStatus> matched = new LinkedHashSet<>();
        for (OrderStatus status : OrderStatus.values()) {
            if (statusLabel(status).toLowerCase(Locale.ROOT).contains(needle)) {
                matched.add(status);
            }
        }
        return matched;
    }

    private RecentOrderRow toRecentOrderRow(Order order) {
        Customer customer = order.getCustomer();
        String customerName = customerNameOf(customer);

        List<OrderItem> items = order.getItems() == null ? List.of() : order.getItems();
        String product = "—";
        String category = "General";

        if (!items.isEmpty()) {
            OrderItem first = items.getFirst();
            String firstName = StringUtils.hasText(first.getItemName()) ? first.getItemName() : "Item";
            product = items.size() > 1
                    ? firstName + " +" + (items.size() - 1) + " more"
                    : firstName;

            if (first.getItem() != null && first.getItem().getItemGroup() != null
                    && StringUtils.hasText(first.getItem().getItemGroup().getName())) {
                category = first.getItem().getItemGroup().getName();
            }
        }

        return new RecentOrderRow(
                order.getId(),
                referenceOf(order),
                customerName,
                initialsOf(customerName),
                null,
                product,
                category,
                orZero(order.getTotal()),
                statusLabel(order.getStatus()));
    }

    private String referenceOf(Order order) {
        String invoice = order.getInvoiceNumber();
        if (StringUtils.hasText(invoice)) {
            return invoice.startsWith("#") ? invoice : "#" + invoice;
        }
        String id = order.getId().toString();
        return "#" + id.substring(id.length() - 4);
    }

    private String customerNameOf(Customer customer) {
        if (customer == null || customer.getGlobalCustomer() == null) {
            return "Walk-in Customer";
        }
        String fullName = customer.getGlobalCustomer().getFullName();
        if (StringUtils.hasText(fullName) && !"customer".equalsIgnoreCase(fullName.trim())) {
            return fullName;
        }
        String email = customer.getGlobalCustomer().getEmail();
        if (StringUtils.hasText(email)) {
            return email.split("@")[0];
        }
        return "Walk-in Customer";
    }

    private String initialsOf(String name) {
        StringBuilder initials = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.isEmpty() ? "WC" : initials.toString();
    }

    /** Said the way the table says it, rather than the way the database stores it. */
    private String statusLabel(OrderStatus status) {
        if (status == null) return "Processing";
        return switch (status) {
            case PAID -> "Paid";
            case CONFIRMED -> "Success";
            case FAILED, CANCELLED -> "Failed";
            case PENDING -> "Processing";
        };
    }

    /**
     * The catalogue ranked by what it sold, ranked and paged by the database.
     *
     * The ranking key is the sales figure, so it cannot be applied after a
     * page has been cut — page two has to be the next five best sellers, not
     * five arbitrary rows sorted among themselves. That is why this reads a
     * query which ranks and pages in one go rather than sorting a list here.
     */
    public PageResponse<BestSellingRow> bestSelling(
            UUID businessId,
            LocalDateTime from,
            LocalDateTime to,
            String search,
            Pageable pageable
    ) {
        businessHelper.findAccessibleBusiness(businessId);

        String needle = StringUtils.hasText(search)
                ? "%" + search.trim().toLowerCase(Locale.ROOT) + "%"
                : null;

        // The ranking is the query's own `order by`, and a native query cannot
        // take a second one appended after it — a caller passing ?sort= would
        // otherwise produce invalid SQL. Only the page is honoured here.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<ItemRepository.BestSellingProjection> page =
                itemRepository.rankBySales(businessId, from, to, needle, unsorted);

        return PageResponse.from(page.map(row -> new BestSellingRow(
                UUID.fromString(row.getItemId()),
                StringUtils.hasText(row.getName()) ? row.getName() : "Product",
                StringUtils.hasText(row.getCategory()) ? row.getCategory() : "General",
                row.getSales() == null ? BigDecimal.ZERO : row.getSales(),
                row.getSold(),
                row.getImageUrl())));
    }


    /* -------------------------------- shared ------------------------------- */

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
