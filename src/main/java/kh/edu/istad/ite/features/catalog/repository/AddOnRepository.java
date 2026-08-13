package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.AddOn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddOnRepository extends JpaRepository<AddOn, UUID> {

    List<AddOn> findByBusinessIdOrderByNameAsc(UUID businessId);

    List<AddOn> findByBusinessIdAndIdIn(UUID businessId, List<UUID> ids);

    Optional<AddOn> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBaseUnitId(UUID unitId);

    boolean existsByBusinessIdAndSlugIgnoreCase(UUID businessId, String slug);

    boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(UUID businessId, String slug, UUID id);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);
}
