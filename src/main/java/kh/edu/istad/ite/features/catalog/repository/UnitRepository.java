package kh.edu.istad.ite.features.catalog.repository;

import kh.edu.istad.ite.features.catalog.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    boolean existsBySlug(String slug);
    boolean existsByNameIgnoreCase(String name);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    List<Unit> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /** Platform units only — the ones every business can select. */
    List<Unit> findByBusinessIsNullOrderByNameAsc();

    /** What one business may pick from: the platform's units and its own. */
    List<Unit> findByBusinessIsNullOrBusinessIdOrderByNameAsc(UUID businessId);

    /** A unit this business may edit. Platform units never match. */
    Optional<Unit> findByIdAndBusinessId(UUID id, UUID businessId);

    /** The unit resolves only if this business is allowed to use it. */
    @Query("""
            select u from Unit u
            where u.id = :unitId
              and (u.business is null or u.business.id = :businessId)
            """)
    Optional<Unit> findSelectableUnit(UUID unitId, UUID businessId);

    boolean existsByBusinessIsNullAndSlugIgnoreCase(String slug);

    boolean existsByBusinessIsNullAndSlugIgnoreCaseAndIdNot(String slug, UUID id);

    boolean existsByBusinessIdAndSlugIgnoreCase(UUID businessId, String slug);

    boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(UUID businessId, String slug, UUID id);

    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);

    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);
}
