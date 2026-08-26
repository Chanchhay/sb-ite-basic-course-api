package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {

        boolean existsByUnit_Id(UUID unitId);

        List<Item> findAllByBusinessIdOrderByNameAsc(UUID businessId);

        Page<Item> findAllByBusinessId(UUID businessId, Pageable pageable);

        Optional<Item> findByIdAndBusinessId(UUID id, UUID businessId);

        boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

        boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);

        boolean existsByBusinessIdAndSlugIgnoreCase(UUID businessId, String slug);

        boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(UUID businessId, String slug, UUID id);

        boolean existsByBusinessIdAndAddOnsId(UUID businessId, UUID addOnId);

    boolean existsByBusinessIdAndItemGroupId(UUID businessId, UUID itemGroupId);

        boolean existsByBusiness_Id(UUID businessId);

        Optional<Item> findByBusinessIdAndBarcode(UUID businessId, String barcode);

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
