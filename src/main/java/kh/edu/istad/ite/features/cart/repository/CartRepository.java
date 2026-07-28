package kh.edu.istad.ite.features.cart.repository;

import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.shared.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCustomerIdAndBusinessIdAndStatus(UUID customerId, UUID businessId, CartStatus status);

    List<Cart> findAllByCustomerIdAndStatus(UUID customerId, CartStatus status);

    List<Cart> findAllByBusinessIdAndStatus(UUID businessId, CartStatus status);

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.item WHERE c.customer.id = :customerId AND c.business.id = :businessId AND c.status = :status")
    Optional<Cart> findActiveCartWithItems(@Param("customerId") UUID customerId, @Param("businessId") UUID businessId, @Param("status") CartStatus status);
}