package kh.edu.istad.ite.features.order.repository;

import kh.edu.istad.ite.features.order.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    List<Sale> findAllByBusinessIdOrderBySoldAtDesc(UUID businessId);

    long countBySoldAtGreaterThanEqual(LocalDateTime since);

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
