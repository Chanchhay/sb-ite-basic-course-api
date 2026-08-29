package kh.edu.istad.ite.features.migration.repository;

import kh.edu.istad.ite.features.migration.entity.MigrationIssue;
import kh.edu.istad.ite.shared.enums.MigrationIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationIssueRepository extends JpaRepository<MigrationIssue, UUID> {

    List<MigrationIssue> findByMigrationIdOrderBySeverityDescAffectedRowsDesc(UUID migrationId);

    Optional<MigrationIssue> findByIdAndMigrationId(UUID id, UUID migrationId);

    /**
     * The decisions already made, so transforming again does not ask twice.
     *
     * An operator who answers forty questions and then re-runs the transform
     * because one column was mapped wrongly should be asked about the column,
     * not about the forty.
     */
    List<MigrationIssue> findByMigrationIdAndStatusNot(UUID migrationId, MigrationIssueStatus status);

    /**
     * Clears the findings of a previous run, keeping resolved ones.
     *
     * A re-transform re-derives what is wrong with the file; it must not
     * re-derive what a person already settled.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MigrationIssue i where i.migration.id = :migrationId and i.status = :status")
    void deleteByMigrationIdAndStatus(
            @Param("migrationId") UUID migrationId,
            @Param("status") MigrationIssueStatus status);

    long countByMigrationIdAndStatus(UUID migrationId, MigrationIssueStatus status);
}
