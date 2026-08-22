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
