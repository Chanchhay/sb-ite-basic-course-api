package kh.edu.istad.ite.features.inventory.repository;

import kh.edu.istad.ite.features.inventory.entity.StockConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StockConsumptionRepository extends JpaRepository<StockConsumption, UUID> {

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
