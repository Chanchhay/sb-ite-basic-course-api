package kh.edu.istad.ite.features.inventory.repository;

import kh.edu.istad.ite.features.inventory.entity.StockLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface StockLayerRepository extends JpaRepository<StockLayer, UUID> {

    /** The FIFO batches an item was received in, cleared when the item is deleted. */
    @Modifying
    @Query("delete from StockLayer l where (l.item is not null and l.item.id = :itemId) or (l.sourceEntry is not null and l.sourceEntry.item.id = :itemId)")
    void deleteByItemId(@Param("itemId") UUID itemId);

    /**
     * Batches with stock left, in the order they are consumed.
     *
     * Soonest to expire first, then — among batches that never expire — oldest
     * arrival first. Arrival order alone is the wrong rotation for anything
     * perishable: a delivery that came in this morning can be short-dated and
     * have to leave before stock that has been here a fortnight, which is the
     * whole point of rotating by date rather than by age.
     *
     * Batches with no expiry queue behind every batch that has one. They keep
     * indefinitely, so nothing is lost by letting dated stock go first.
     *
     * One query for both kinds of target: exactly one of the two ids is set on
     * a layer, so passing the other as null matches nothing.
     *
     * On an item, the option narrows it further, and a null option means the
     * batches held against the item as a whole — not "any option". Matching
     * every option there would cost a sale of Large from Small's deliveries.
     */
    @Query("""
            select l from StockLayer l
            where l.business.id = :businessId
              and l.quantityRemaining > 0
              and (
                  (:itemId is not null and l.item.id = :itemId
                      and (
                          (:variantId is null and l.variant is null)
                          or l.variant.id = :variantId
                      ))
                  or (:addOnId is not null and l.addOn.id = :addOnId)
              )
            order by l.expiresAt asc nulls last, l.receivedAt asc, l.id asc
            """)
    List<StockLayer> findOpenLayers(UUID businessId, UUID itemId, UUID variantId, UUID addOnId);

    /**
     * Every open batch on an item, whichever option it arrived for, in the
     * same order they will be consumed in.
     *
     * What the item costs as a whole, for readers that do not care which
     * option a unit came from. {@link #findOpenLayers} cannot answer this: a
     * null option there means the item's own batches, not all of them.
     */
    @Query("""
            select l from StockLayer l
            where l.business.id = :businessId
              and l.quantityRemaining > 0
              and l.item.id = :itemId
            order by l.expiresAt asc nulls last, l.receivedAt asc, l.id asc
            """)
    List<StockLayer> findOpenItemLayers(UUID businessId, UUID itemId);

    /**
     * Every batch in the business still holding stock, in consumption order.
     *
     * What stock is worth can only be answered here: each batch kept the price
     * it arrived at, so the value of what is left is the sum of what each
     * still holds times what that batch cost. One cost per item cannot say it
     * once two deliveries at different prices are both on the shelf.
     */
    @Query("""
            select l from StockLayer l
            where l.business.id = :businessId
              and l.quantityRemaining > 0
            order by l.expiresAt asc nulls last, l.receivedAt asc, l.id asc
            """)
    List<StockLayer> findAllOpenLayers(UUID businessId);

    boolean existsByBusiness_IdAndItem_Id(UUID businessId, UUID itemId);

    @Query("select coalesce(sum(l.quantityRemaining), 0) from StockLayer l where l.business.id = :businessId and l.item.id = :itemId")
    BigDecimal sumRemainingForItem(UUID businessId, UUID itemId);
}
