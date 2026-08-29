package kh.edu.istad.ite.features.migration.repository;

import kh.edu.istad.ite.features.migration.entity.AssistedMigrationSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssistedMigrationSourceRepository
        extends JpaRepository<AssistedMigrationSource, UUID> {

    /**
     * A migration's files in the order they were added.
     *
     * The order is meaningful, not cosmetic: the first is the source whose
     * records become items, and everything else enriches it.
     */
    List<AssistedMigrationSource> findByMigrationIdOrderByOrdinalAsc(UUID migrationId);

    Optional<AssistedMigrationSource> findByIdAndMigrationId(UUID id, UUID migrationId);

    long countByMigrationId(UUID migrationId);
}
