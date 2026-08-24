package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemGroupRepository extends JpaRepository<ItemGroup, UUID> {

    List<ItemGroup> findByBusinessIdAndParentIsNullOrderByNameAsc(UUID businessId);

    Page<ItemGroup> findByBusinessIdAndParentIsNull(UUID businessId, Pageable pageable);

    List<ItemGroup> findByBusinessIdAndParentIsNotNullOrderByNameAsc(UUID businessId);

    Optional<ItemGroup> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndSlugIgnoreCase(UUID businessId, String slug);

    boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(UUID businessId, String slug, UUID id);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);

    boolean existsByBusinessIdAndParentId(UUID businessId, UUID parentId);
}
