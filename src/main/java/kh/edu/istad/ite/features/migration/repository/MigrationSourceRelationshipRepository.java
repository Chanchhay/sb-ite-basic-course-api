package kh.edu.istad.ite.features.migration.repository;

import kh.edu.istad.ite.features.migration.entity.MigrationSourceRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MigrationSourceRelationshipRepository
        extends JpaRepository<MigrationSourceRelationship, UUID> {

    List<MigrationSourceRelationship> findByMigrationId(UUID migrationId);

    /**
     * Clears a migration's joins so a fresh set can replace them.
     *
     * Saved all at once rather than edited one at a time: the joins only make
     * sense together, and half-applying a change would leave a migration whose
     * sources are connected in a way nobody chose.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MigrationSourceRelationship r where r.migration.id = :migrationId")
    void deleteByMigrationId(@Param("migrationId") UUID migrationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MigrationSourceRelationship r "
            + "where r.migration.id = :migrationId "
            + "and (r.leftSourceId = :sourceId or r.rightSourceId = :sourceId)")
    void deleteByMigrationIdAndSource(
            @Param("migrationId") UUID migrationId,
            @Param("sourceId") UUID sourceId);
}
