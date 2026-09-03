package kh.edu.istad.ite.features.order.repository;

import kh.edu.istad.ite.features.order.entity.OrderItem;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Modifying
    @Query("update OrderItem oi set oi.item = null, oi.variant = null where oi.item.id = :itemId")
    void detachItem(@Param("itemId") UUID itemId);

    /** Whether this item is on an order still in one of the given statuses. */
    boolean existsByItem_IdAndOrder_StatusIn(UUID itemId, Collection<OrderStatus> statuses);
}
