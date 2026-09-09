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

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Page<Order> findAllByBusinessId(UUID businessId, Pageable pageable);

    /**
     * The business's orders, filtered and paged by the database.
     *
     * The dashboard's table used to read a fixed window of recent orders and
     * sift it in memory, which meant a search could only ever find what that
     * window already held — an invoice one order too old was simply absent,
     * with nothing to say so. Here the filter and the page are the same
     * query, so a search reaches the whole history and the count it reports
     * is the real one.
     *
     * `search` is matched, case-insensitively, against the invoice number,
     * the customer's name, and the name of any item on the order. Statuses
     * are passed separately because the table names them its own way
     * ("Success" for CONFIRMED), so the caller translates the words it shows
     * back into the values stored before asking.
     *
     * The item match is an `exists` rather than a join: joining a collection
     * multiplies the order out by its lines, which would both duplicate rows
     * and make the page size mean something other than orders.
     */
    @Query("""
            select o from Order o
            left join o.customer c
            left join c.globalCustomer gc
            where o.business.id = :businessId
              and (
                :search is null
                or lower(coalesce(o.invoiceNumber, '')) like :search
                or lower(coalesce(gc.fullName, '')) like :search
                or lower(coalesce(gc.email, '')) like :search
                or exists (
                    select 1 from OrderItem oi
                    where oi.order = o
                      and lower(coalesce(oi.itemName, '')) like :search
                )
                or (:hasStatuses = true and o.status in :statuses)
              )
            """)
    Page<Order> searchForDashboard(
            @Param("businessId") UUID businessId,
            /** Already lowercased and wrapped in %…%, or null for no filter. */
            @Param("search") String search,
            @Param("hasStatuses") boolean hasStatuses,
            @Param("statuses") Collection<OrderStatus> statuses,
            Pageable pageable);
    Optional<Order> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<Order> findByBusinessIdAndInvoiceNumber(UUID businessId, String invoiceNumber);
    boolean existsByInvoiceNumber(String invoiceNumber);
    long countByBusinessId(UUID businessId);

    long countByCashierIdAndCreatedDateBetween(UUID cashierId, java.time.LocalDateTime start, java.time.LocalDateTime end);
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

    long countByBusinessIdAndCustomerIdAndDiscountCodeIgnoreCaseAndStatusNot(
            UUID businessId,
            UUID customerId,
            String discountCode,
            OrderStatus status
    );


    // Pay Later never touches Bakong/KHQR, so an order sitting there waiting
    // on the business to approve it must never count as an "open order" —
    // that would block a shopper from paying (or Pay-Latering) anywhere
    // else, at this shop or another, purely because a *different* shop
    // hasn't reviewed an earlier Pay Later order yet.
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.business
            WHERE o.customer.id IN :customerIds
              AND o.channel IN :channels
              AND o.status = :status
              AND o.awaitingPayLaterApproval = false
            ORDER BY o.createdDate DESC
            """)
    List<Order> findOpenOrdersForShopper(
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("channels") Collection<OrderChannel> channels,
            @Param("status") OrderStatus status);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.business
            LEFT JOIN FETCH o.items i
            WHERE o.customer.id IN :customerIds
            ORDER BY o.createdDate DESC
            """)
    List<Order> findAllOrdersForShopper(@Param("customerIds") Collection<UUID> customerIds);

    /**
     * The business's unfinished orders, lines included.
     *
     * Used when the base currency moves: an order still being rung up holds
     * prices in the old base, and leaving them behind would label new-base
     * amounts with the old code and convert them a second time on the
     * secondary line. Settled orders are deliberately not here — a receipt
     * has to keep showing the figures the customer was actually handed.
     */
    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.items
            WHERE o.business.id = :businessId
              AND o.status = :status
            """)
    List<Order> findAllWithItemsByBusinessIdAndStatus(
            @Param("businessId") UUID businessId,
            @Param("status") OrderStatus status);
}