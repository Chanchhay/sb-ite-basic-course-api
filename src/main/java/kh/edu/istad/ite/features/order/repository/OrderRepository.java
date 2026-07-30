package kh.edu.istad.ite.features.order.repository;

import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.shared.enums.OrderChannel;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndBusinessId(UUID id, UUID businessId);

    long countByBusinessId(UUID businessId);
    @Query("""
            SELECT o FROM Order o
            WHERE o.business.id = :businessId
              AND o.customer.id = :customerId
              AND o.status IN :statuses
            ORDER BY o.createdDate DESC
            """)
    Page<Order> findForCustomerByStatuses(
            @Param("businessId") UUID businessId,
            @Param("customerId") UUID customerId,
            @Param("statuses") Collection<OrderStatus> statuses,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.item
            WHERE o.id = :orderId
              AND o.business.id = :businessId
              AND o.customer.id = :customerId
            """)
    Optional<Order> findDetailForCustomer(
            @Param("orderId") UUID orderId,
            @Param("businessId") UUID businessId,
            @Param("customerId") UUID customerId);

    long countByBusinessIdAndCustomerIdAndStatus(UUID businessId, UUID customerId, OrderStatus status);


    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.business
            WHERE o.customer.id IN :customerIds
              AND o.channel = :channel
              AND o.status = :status
            ORDER BY o.createdDate DESC
            """)
    List<Order> findOpenOrdersForShopper(
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("channel") OrderChannel channel,
            @Param("status") OrderStatus status);
}