package kh.edu.istad.ite.features.inventory.repository;

import kh.edu.istad.ite.features.inventory.entity.StockConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StockConsumptionRepository extends JpaRepository<StockConsumption, UUID> {

    /**
     * Clears what an item's batches were drawn down by, so the item can go.
     *
     * Deleting an item takes its whole ledger with it — consumptions, then the
     * layers they drew from, then the entries that made those layers. In that
     * order, because each points at the one before it.
     */
    @Modifying
    @Query("delete from StockConsumption c where (c.stockLayer is not null and c.stockLayer.item.id = :itemId) or (c.stockEntry is not null and c.stockEntry.item.id = :itemId)")
    void deleteByItemId(@Param("itemId") UUID itemId);

    List<StockConsumption> findByStockEntry_IdOrderByCreatedDateAsc(UUID stockEntryId);

    /**
     * What one movement took, batch by batch, in the order it took them.
     *
     * The layer is fetched alongside: every row names the lot and dates of the
     * delivery it came from, and without the join each row of the breakdown
     * would be a query of its own.
     */
    @Query("""
            select c from StockConsumption c
            join fetch c.stockLayer l
            where c.stockEntry.id = :stockEntryId
            order by l.expiresAt asc nulls last, l.receivedAt asc, l.id asc
            """)
    List<StockConsumption> findBreakdown(UUID stockEntryId);
}
