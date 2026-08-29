package kh.edu.istad.ite.features.migration.repository;

import kh.edu.istad.ite.features.migration.entity.AssistedMigration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssistedMigrationRepository extends JpaRepository<AssistedMigration, UUID> {

    /**
     * The migration, provided it belongs to this business.
     *
     * Every read and write goes through this rather than {@code findById}. An
     * operator works across businesses, so the business is not implied by who
     * they are — it has to be checked against the job itself, or a mistyped id
     * would show one customer's catalogue while claiming to be another's.
     */
    Optional<AssistedMigration> findByIdAndBusinessId(UUID id, UUID businessId);

    Page<AssistedMigration> findByBusinessIdOrderByCreatedDateDesc(UUID businessId, Pageable pageable);

    Page<AssistedMigration> findAllByOrderByCreatedDateDesc(Pageable pageable);
}
