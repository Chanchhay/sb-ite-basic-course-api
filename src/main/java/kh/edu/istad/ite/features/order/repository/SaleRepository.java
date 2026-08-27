package kh.edu.istad.ite.features.order.repository;

import kh.edu.istad.ite.features.order.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    /** How each of these orders was paid, for a page of order history rows. */
    List<Sale> findByOrder_IdIn(Collection<UUID> orderIds);

    List<Sale> findAllByBusinessIdOrderBySoldAtDesc(UUID businessId);

    /** Sales rung up as "pay later" that still owe money. */
    @Query("select s from Sale s where s.business.id = :businessId and s.paidAmount < s.totalAmount order by s.soldAt desc")
    List<Sale> findUnsettledByBusinessId(@Param("businessId") UUID businessId);

    long countBySoldAtGreaterThanEqual(LocalDateTime since);

    @Query("select coalesce(sum(s.totalAmount), 0) from Sale s where cast(s.cashierId as string) = :cashierId and s.paymentMethod = 'CASH' and s.soldAt >= :startTime and (cast(:endTime as timestamp) is null or s.soldAt <= :endTime)")
    java.math.BigDecimal sumCashSalesByCashierAndDateRange(
            @Param("cashierId") String cashierId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("select count(distinct s.business.id) from Sale s where s.soldAt >= :since")
    long countTradingBusinessesSince(@Param("since") LocalDateTime since);

    @Query(value = "select to_char(sold_at, 'YYYY-MM') as month, count(*) as count "
            + "from sales where sold_at >= :since "
            + "group by to_char(sold_at, 'YYYY-MM') order by month",
            nativeQuery = true)
    List<MonthlyCountProjection> countGroupedByMonth(@Param("since") LocalDateTime since);

    @Query("select s.business.id as businessId, s.business.displayName as businessName, "
            + "count(s) as orders, coalesce(sum(s.itemCount), 0) as itemsSold "
            + "from Sale s where s.soldAt >= :since "
            + "group by s.business.id, s.business.displayName order by count(s) desc")
    List<ActiveBusinessProjection> findMostActiveBusinessesSince(@Param("since") LocalDateTime since);

    /**
     * What each channel took and what it cost, over a range.
     *
     * Grouped in the database rather than totalled by the caller: a shop that
     * has been trading a year has more sales than any one read should carry,
     * and a total that quietly stops at a thousand rows is worse than none.
     *
     * Cost is the sum of what the stock actually cost, batch by batch, as it
     * was recorded at the moment of each sale — not what the shelf costs
     * today. That is the whole point of keeping it on the sale.
     */
    @Query("""
            select s.channel as channel,
                   count(s) as sales,
                   coalesce(sum(s.itemCount), 0) as itemsSold,
                   coalesce(sum(s.subtotal), 0) as grossSales,
                   coalesce(sum(s.discountAmount), 0) as discounts,
                   coalesce(sum(s.totalAmount), 0) as revenue,
                   coalesce(sum(s.totalCost), 0) as cost
            from Sale s
            where s.business.id = :businessId
              and (cast(:from as timestamp) is null or s.soldAt >= :from)
              and (cast(:to as timestamp) is null or s.soldAt <= :to)
            group by s.channel
            order by sum(s.totalAmount) desc
            """)
    List<ChannelProfitProjection> profitByChannel(
            @Param("businessId") UUID businessId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    interface ChannelProfitProjection {
        kh.edu.istad.ite.shared.enums.OrderChannel getChannel();
        long getSales();
        long getItemsSold();
        java.math.BigDecimal getGrossSales();
        java.math.BigDecimal getDiscounts();
        java.math.BigDecimal getRevenue();
        java.math.BigDecimal getCost();
    }


    @Query(value = """
            select to_char(sold_at, 'YYYY-MM-DD') as day,
                   channel as channel,
                   coalesce(sum(total_amount), 0) as revenue
            from sales
            where business_owner_id = :businessId
              and (cast(:from as timestamp) is null or sold_at >= :from)
              and (cast(:to as timestamp) is null or sold_at <= :to)
            group by to_char(sold_at, 'YYYY-MM-DD'), channel
            order by day
            """, nativeQuery = true)
    List<DailyChannelRevenueProjection> dailyRevenueByChannel(
            @Param("businessId") UUID businessId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * What the shop made, sliced into calendar periods.
     *
     * Native, and grouped by {@code date_trunc}, because the calendar
     * arithmetic is the database's: weeks begin where Postgres says they
     * begin, and months and years land correctly across the boundaries that
     * catch a hand-rolled version out. The field is bound as a parameter, so
     * the granularity never reaches the query as text.
     *
     * Revenue is net of tax. Tax charged is money held for the tax authority,
     * so leaving it in would inflate profit and margin by whatever the shop
     * happens to charge. It is returned in its own column instead.
     *
     * Grouped by the first output column rather than by repeating the
     * expression. The same named parameter used twice is bound as two separate
     * placeholders, so `date_trunc($1, sold_at)` in the select and
     * `date_trunc($2, sold_at)` in the group by are not the same expression to
     * Postgres — it sees an ungrouped `sold_at` and refuses the query. The
     * ordinal names the one expression once.
     *
     * Ordering by that same column is chronological because the label is
     * `YYYY-MM-DD`, which sorts as a string exactly as it does as a date.
     *
     * Newest first: a shop opening this report wants the period it is in.
     */
    @Query(value = """
            select to_char(date_trunc(:granularity, sold_at), 'YYYY-MM-DD') as periodStart,
                   count(*) as sales,
                   coalesce(sum(item_count), 0) as itemsSold,
                   coalesce(sum(subtotal), 0) as grossSales,
                   coalesce(sum(discount_amount), 0) as discounts,
                   coalesce(sum(tax_amount), 0) as tax,
                   coalesce(sum(total_amount - tax_amount), 0) as revenue,
                   coalesce(sum(total_cost), 0) as cost
            from sales
            where business_owner_id = :businessId
              and (cast(:from as timestamp) is null or sold_at >= :from)
              and (cast(:to as timestamp) is null or sold_at <= :to)
            group by 1
            order by 1 desc
            """, nativeQuery = true)
    List<PeriodProfitProjection> profitByPeriod(
            @Param("businessId") UUID businessId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("granularity") String granularity
    );

    interface PeriodProfitProjection {
        String getPeriodStart();
        long getSales();
        long getItemsSold();
        java.math.BigDecimal getGrossSales();
        java.math.BigDecimal getDiscounts();
        java.math.BigDecimal getTax();
        java.math.BigDecimal getRevenue();
        java.math.BigDecimal getCost();
    }

    /**
     * What each item sold over a range, and what it cost.
     *
     * Joined from the sale rather than the order, so only what was actually
     * paid for counts and the range filters on when the money came in. An
     * abandoned basket is not a sale and has no place in a profit report.
     *
     * Cost is {@code unit_cost * quantity * unit_factor}: `unit_cost` is what
     * one *base* unit cost, so a case of twenty-four costs twenty-four times
     * it. The extras chosen on the line are added to it, because the line's
     * revenue already includes what they were charged at — counting the price
     * of a topping but not its cost would report a margin nobody made.
     *
     * This is the same arithmetic the sale's own total cost uses, so the items
     * add up to the statement rather than to a second number that disagrees
     * with it. Lines sold before add-on cost was recorded carry zero for it,
     * and so do the sales they belong to, so the two agree there as well.
     *
     * Grouped by the option as well as the item: a shop that loses money on
     * Large and makes it on Small has learned nothing from the two averaged.
     */
    @Query(value = """
            select cast(oi.item_id as varchar) as itemId,
                   cast(oi.variant_id as varchar) as variantId,
                   min(oi.item_name) as itemName,
                   min(v.variant_name) as variantName,
                   coalesce(sum(oi.quantity), 0) as quantitySold,
                   count(*) as lines,
                   coalesce(sum(oi.discount_amount), 0) as discounts,
                   coalesce(sum(oi.line_total), 0) as revenue,
                   coalesce(sum(oi.unit_cost * oi.quantity
                                * coalesce(oi.unit_factor, 1)), 0)
                     + coalesce(sum(extras.cost), 0) as cost
            from order_items oi
            join sales s on s.order_id = oi.order_id
            left join item_variants v on v.id = oi.variant_id
            -- Summed per line before joining, so a drink with two extras is
            -- not counted twice over by the join fanning its line out.
            left join (
                select order_item_id, sum(cost) as cost
                from order_item_add_ons
                group by order_item_id
            ) extras on extras.order_item_id = oi.id
            where s.business_owner_id = :businessId
              and (cast(:from as timestamp) is null or s.sold_at >= :from)
              and (cast(:to as timestamp) is null or s.sold_at <= :to)
            group by oi.item_id, oi.variant_id
            order by sum(oi.line_total) desc
            """, nativeQuery = true)
    List<ItemProfitProjection> profitByItem(
            @Param("businessId") UUID businessId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    interface ItemProfitProjection {
        String getItemId();
        String getVariantId();
        String getItemName();
        String getVariantName();
        long getQuantitySold();
        long getLines();
        java.math.BigDecimal getDiscounts();
        java.math.BigDecimal getRevenue();
        java.math.BigDecimal getCost();
    }

    interface DailyChannelRevenueProjection {
        String getDay();
        String getChannel();
        java.math.BigDecimal getRevenue();
    }

    interface MonthlyCountProjection {
        String getMonth();
        long getCount();
    }

    interface ActiveBusinessProjection {
        UUID getBusinessId();
        String getBusinessName();
        long getOrders();
        long getItemsSold();
    }
}
