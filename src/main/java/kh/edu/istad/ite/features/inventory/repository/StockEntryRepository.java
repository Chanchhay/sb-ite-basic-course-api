package kh.edu.istad.ite.features.inventory.repository;

import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StockEntryRepository extends JpaRepository<StockEntry, UUID>, JpaSpecificationExecutor<StockEntry> {

    Optional<StockEntry> findByIdAndBusiness_Id(UUID id, UUID businessId);

    Optional<StockEntry> findFirstByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(UUID businessId, UUID itemId);

    /**
     * The latest movement on one option of an item — the end of its balance
     * chain, and what the next movement counts on from.
     */
    Optional<StockEntry> findFirstByBusiness_IdAndItem_IdAndVariant_IdOrderByCreatedDateDescIdDesc(
            UUID businessId, UUID itemId, UUID variantId);

    /**
     * The same, for stock held against the item as a whole: an item with no
     * options, or quantities recorded before it had any.
     */
    Optional<StockEntry> findFirstByBusiness_IdAndItem_IdAndVariantIsNullOrderByCreatedDateDescIdDesc(
            UUID businessId, UUID itemId);

    Optional<StockEntry> findFirstByBusiness_IdAndAddOn_IdOrderByCreatedDateDescIdDesc(UUID businessId, UUID addOnId);

    List<StockEntry> findAllByBusiness_IdAndItem_IdOrderByCreatedDateDescIdDesc(UUID businessId, UUID itemId);

    List<StockEntry> findAllByBusiness_IdAndAddOn_IdOrderByCreatedDateDescIdDesc(UUID businessId, UUID addOnId);

    List<StockEntry> findAllByBusiness_IdOrderByCreatedDateDescIdDesc(UUID businessId);

    /**
     * The items in this business that already have a stock history.
     *
     * Asked once by a data migration, which has to know for every row at once
     * whether an opening balance would be the item's first — the ledger allows
     * only one, so a re-import must leave those items alone. One query rather
     * than one per row.
     */
    @Query("""
            select distinct entry.item.id from StockEntry entry
             where entry.business.id = :businessId
               and entry.item.id is not null
            """)
    Set<UUID> findItemIdsWithStockHistory(@Param("businessId") UUID businessId);
}
