package kh.edu.istad.ite.features.migration.repository;

import kh.edu.istad.ite.features.migration.entity.MigrationEntityLink;
import kh.edu.istad.ite.shared.enums.MigrationEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationEntityLinkRepository extends JpaRepository<MigrationEntityLink, UUID> {

    /** What their identifier became here, which is the question a delta asks. */
    Optional<MigrationEntityLink> findByBusinessIdAndSourceSystemAndSourceEntityTypeAndSourceEntityId(
            UUID businessId, String sourceSystem, String sourceEntityType, String sourceEntityId);

    List<MigrationEntityLink> findByMigrationId(UUID migrationId);

    List<MigrationEntityLink> findByBusinessIdAndFluxibizEntityType(
            UUID businessId, MigrationEntityType fluxibizEntityType);
}
