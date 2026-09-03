package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

        boolean existsByUnit_Id(UUID unitId);

        List<Item> findAllByBusinessIdOrderByNameAsc(UUID businessId);

        /**
         * The catalogue ranked by what it actually sold, filtered and paged
         * by the database.
         *
         * Revenue is {@code sum(line_total)} over sold lines — deliberately
         * the same arithmetic {@code SaleRepository.profitByItem} uses, so
         * the table and the chart beside it cannot quote different figures
         * for the same item.
         *
         * A line only counts once its order has become a sale: the join to
         * {@code sales} is a left join so that an item which has never sold
         * still appears with zero, and the {@code case} inside the sums is
         * what stops an unpaid order from being counted as revenue. Putting
         * that condition in the {@code where} instead would quietly turn the
         * outer join back into an inner one and drop every unsold item.
         *
         * Options are summed back into their item, because the table lists
         * items; the chart that wants them apart reads the report directly.
         */
        @Query(value = """
                select cast(i.id as varchar)   as itemId,
                       i.name                  as name,
                       g.name                  as category,
                       coalesce(sum(case when s.id is not null then oi.line_total else 0 end), 0) as sales,
                       coalesce(sum(case when s.id is not null then oi.quantity   else 0 end), 0) as sold,
                       i.image_url             as imageUrl
                from items i
                left join item_groups g on g.id = i.item_group_id
                left join order_items oi on oi.item_id = i.id
                left join sales s on s.order_id = oi.order_id
                     and s.business_owner_id = :businessId
                     and (cast(:from as timestamp) is null or s.sold_at >= :from)
                     and (cast(:to as timestamp) is null or s.sold_at <= :to)
                where i.business_owner_id = :businessId
                  and i.is_deleted = false
                  and (:search is null or lower(i.name) like :search)
                group by i.id, i.name, g.name, i.image_url
                order by 4 desc, i.name asc
                """,
                countQuery = """
                select count(*)
                from items i
                where i.business_owner_id = :businessId
                  and i.is_deleted = false
                  and (:search is null or lower(i.name) like :search)
                """,
                nativeQuery = true)
        Page<BestSellingProjection> rankBySales(
                @Param("businessId") UUID businessId,
                @Param("from") LocalDateTime from,
                @Param("to") LocalDateTime to,
                /** Already lowercased and wrapped in %…%, or null for no filter. */
                @Param("search") String search,
                Pageable pageable);

        interface BestSellingProjection {
                String getItemId();
                String getName();
                String getCategory();
                java.math.BigDecimal getSales();
                long getSold();
                String getImageUrl();
        }

        Page<Item> findAllByBusinessId(UUID businessId, Pageable pageable);

        /**
         * The same page, with the trash left out.
         *
         * This is what the catalogue list is actually asking for: an item in
         * the recycle bin is not on sale, and showing it alongside live stock
         * is how a shop ends up scanning something it has already deleted.
         * The bin has its own listing through the search endpoint.
         */
        Page<Item> findAllByBusinessIdAndIsDeletedFalse(UUID businessId, Pageable pageable);

        Optional<Item> findByIdAndBusinessId(UUID id, UUID businessId);

        /** Same lookup, but blind to anything sitting in the trash. */
        Optional<Item> findByIdAndBusinessIdAndIsDeletedFalse(UUID id, UUID businessId);

        boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

        boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);

        boolean existsByBusinessIdAndSlugIgnoreCase(UUID businessId, String slug);

        boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(UUID businessId, String slug, UUID id);

        boolean existsByBusinessIdAndAddOnsId(UUID businessId, UUID addOnId);

    boolean existsByBusinessIdAndItemGroupId(UUID businessId, UUID itemGroupId);

        boolean existsByBusiness_Id(UUID businessId);

        Optional<Item> findByBusinessIdAndBarcode(UUID businessId, String barcode);

        /**
         * The live item this barcode belongs to.
         *
         * Barcodes carry no unique constraint, so a scan can land on more than
         * one row once a trashed item and a newer one happen to share a code —
         * {@link #findByBusinessIdAndBarcode} would then blow up with a
         * non-unique result. Trash is invisible to POS anyway, so it is
         * excluded here rather than left to collide.
         */
        Optional<Item> findByBusinessIdAndBarcodeAndIsDeletedFalse(UUID businessId, String barcode);

        /**
         * Items matching a SKU, ignoring case.
         *
         * A list rather than an optional because nothing in the schema stops a
         * shop having two items with the same SKU — the unique constraints are
         * on name and slug — and a migration must not blow up on a catalogue
         * that was already like that.
         */
        List<Item> findByBusinessIdAndSkuIgnoreCase(UUID businessId, String sku);

        /** The same, by name, which the catalogue does keep unique per shop. */
        List<Item> findByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

        Page<Item> findByBusinessIdAndStatusAndItemGroup_IdOrderByNameAsc(
                        UUID businessId, ItemStatus status, UUID itemGroupId, Pageable pageable);

        Page<Item> findByBusinessIdAndStatusOrderByNameAsc(
                        UUID businessId, ItemStatus status, Pageable pageable);

        Page<Item> findByBusinessIdAndStatusAndNameContainingIgnoreCaseOrderByNameAsc(
                        UUID businessId, ItemStatus status, String name, Pageable pageable);

        Page<Item> findByBusinessIdAndStatusAndPriceBetweenOrderByNameAsc(
                        UUID businessId, ItemStatus status, BigDecimal minPrice, BigDecimal maxPrice,
                        Pageable pageable);

}
