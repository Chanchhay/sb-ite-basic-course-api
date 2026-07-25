package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemGroupRepository extends JpaRepository<ItemGroup, UUID> {

    List<ItemGroup> findByBusinessIdAndParentIsNullOrderByNameAsc(UUID businessId);

    List<ItemGroup> findByBusinessIdAndParentIsNotNullOrderByNameAsc(UUID businessId);

    Optional<ItemGroup> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndSlugIgnoreCase(UUID businessId, String slug);

    boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(UUID businessId, String slug, UUID id);

    boolean existsByBusinessIdAndParentId(UUID businessId, UUID parentId);
}
