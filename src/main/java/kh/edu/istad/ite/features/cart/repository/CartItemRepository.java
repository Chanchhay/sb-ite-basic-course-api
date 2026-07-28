package kh.edu.istad.ite.features.cart.repository;

import kh.edu.istad.ite.features.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCart_IdOrderByCreatedDateAsc(UUID cartId);

    Optional<CartItem> findByCart_IdAndItem_Id(UUID cartId, UUID itemId);
}