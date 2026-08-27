package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ItemVariantRepository extends JpaRepository<ItemVariant, UUID> {

    Optional<ItemVariant> findByIdAndBusiness_Id(UUID id, UUID businessId);

    List<ItemVariant> findAllByBusiness_Id(UUID businessId);

    /**
     * The items in this business that are sold in options.
     *
     * Stock on such an item belongs to one of its options, never to the item
     * as a whole, so a data migration needs to know which items these are
     * before it offers to post an opening balance against any of them.
     */
    @Query("""
            select distinct variant.item.id from ItemVariant variant
             where variant.business.id = :businessId
            """)
    Set<UUID> findItemIdsWithVariants(@Param("businessId") UUID businessId);
}
