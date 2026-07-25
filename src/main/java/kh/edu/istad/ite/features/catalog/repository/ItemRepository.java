package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    boolean existsByUnit_Id(UUID unitId);
    List<Item> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<Item> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);

    boolean existsByBusinessIdAndSlugIgnoreCase(UUID businessId, String slug);

    boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(UUID businessId, String slug, UUID id);

    boolean existsByBusinessIdAndItemGroupId(UUID businessId, UUID itemGroupId);

    boolean existsByBusiness_Id(UUID businessId);
}
