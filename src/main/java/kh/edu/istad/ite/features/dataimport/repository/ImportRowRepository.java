package kh.edu.istad.ite.features.dataimport.repository;

import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ImportRowRepository extends JpaRepository<ImportRow, UUID> {

    Page<ImportRow> findByImportJobIdOrderByRowNumberAsc(UUID importJobId, Pageable pageable);

    Page<ImportRow> findByImportJobIdAndStatusOrderByRowNumberAsc(
            UUID importJobId, ImportRowStatus status, Pageable pageable);

    Page<ImportRow> findByImportJobIdAndStatusInOrderByRowNumberAsc(
            UUID importJobId, Collection<ImportRowStatus> statuses, Pageable pageable);

    List<ImportRow> findByImportJobIdAndStatusInOrderByRowNumberAsc(
            UUID importJobId, Collection<ImportRowStatus> statuses);

    long countByImportJobIdAndStatus(UUID importJobId, ImportRowStatus status);

    /**
     * Clears the staged rows so checking can be run again.
     *
     * Re-checking always starts from the file, never from what a previous run
     * concluded: the user may have changed the column matching, and rows
     * judged against the old matching would otherwise survive into the new
     * result.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ImportRow row where row.importJob.id = :importJobId")
    int deleteByImportJobId(@Param("importJobId") UUID importJobId);

    @Query("""
            select count(row) from ImportRow row
             where row.importJob.id = :importJobId
               and row.status in :statuses
            """)
    long countByStatuses(
            @Param("importJobId") UUID importJobId,
            @Param("statuses") Collection<ImportRowStatus> statuses
    );
}
