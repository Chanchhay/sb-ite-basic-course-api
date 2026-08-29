package kh.edu.istad.ite.features.discount.repository;

import kh.edu.istad.ite.features.discount.entity.Discount;
import kh.edu.istad.ite.features.discount.entity.DiscountTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiscountTargetRepository extends JpaRepository<DiscountTarget, UUID> {

    List<DiscountTarget> findAllByDiscountId(UUID discount);

    List<DiscountTarget> findAllByDiscountIdIn(List<UUID> discountIds);

    void deleteAllByDiscountId(UUID discount);

    boolean existsByDiscountIdAndItemId(UUID discountId, UUID itemId);

    boolean existsByDiscountIdAndItemGroupId(UUID discountId, UUID itemGroupId);

    // Discounts that are scoped to this exact item, used by "what discounts
    // apply to this product" lookups (POS / website checkout, etc.).
    List<DiscountTarget> findAllByItemIdAndDiscount_BusinessId(UUID itemId, UUID businessId);

    // Discounts that are scoped to this exact category.
    List<DiscountTarget> findAllByItemGroupIdAndDiscount_BusinessId(UUID itemGroupId, UUID businessId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT dt FROM DiscountTarget dt
        JOIN dt.discount d
        WHERE d.business.id = :businessId
          AND d.status = 'ACTIVE'
          AND dt.targetType = 'ITEM'
          AND dt.item.id IN :itemIds
          AND (:excludeDiscountId IS NULL OR d.id != :excludeDiscountId)
    """)
    List<DiscountTarget> findActiveItemTargetsByBusinessIdAndItemIds(
            @org.springframework.data.repository.query.Param("businessId") UUID businessId,
            @org.springframework.data.repository.query.Param("itemIds") List<UUID> itemIds,
            @org.springframework.data.repository.query.Param("excludeDiscountId") UUID excludeDiscountId
    );
}
