package kh.edu.istad.ite.features.cart.repository;

import kh.edu.istad.ite.features.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    Optional<CartItem> findByCartIdAndItemId(UUID cartId, UUID itemId);
    Optional<CartItem> findByCartIdAndItemIdAndVariantIsNull(UUID cartId, UUID itemId);

    Optional<CartItem> findByCartIdAndItemIdAndVariant_Id(UUID cartId, UUID itemId, UUID variantId);

    List<CartItem> findAllByCartId(UUID cartId);

    List<CartItem> findAllByCart_Business_Id(UUID businessId);

    void deleteByCartIdAndItemId(UUID cartId, UUID itemId);

    void deleteAllByCartId(UUID cartId);
}