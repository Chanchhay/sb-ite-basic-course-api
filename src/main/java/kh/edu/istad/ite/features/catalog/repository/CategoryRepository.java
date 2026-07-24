package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByBusinessIdAndParentIsNullOrderByNameAsc(UUID businessId);

    List<Category> findByBusinessIdAndParentIsNotNullOrderByNameAsc(UUID businessId);

    Optional<Category> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndSlugIgnoreCase(UUID businessId, String slug);

    boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(UUID businessId, String slug, UUID id);

    boolean existsByBusinessIdAndParentId(UUID businessId, UUID parentId);
}
