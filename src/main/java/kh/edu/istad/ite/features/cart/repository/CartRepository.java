package kh.edu.istad.ite.features.cart.repository;

import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.shared.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByBusiness_IdAndCustomer_IdAndStatus(UUID businessId, UUID customerId, CartStatus status);

}