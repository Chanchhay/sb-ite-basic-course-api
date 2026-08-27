package kh.edu.istad.ite.features.dataimport.repository;

import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    /**
     * The job, provided it belongs to this business.
     *
     * Every read and write goes through this rather than {@code findById}: an
     * import job id is the only handle a client gets on an uploaded file, and
     * one shop must never be able to read another's catalogue by quoting one.
     */
    Optional<ImportJob> findByIdAndBusinessId(UUID id, UUID businessId);

    Page<ImportJob> findByBusinessIdOrderByCreatedDateDesc(UUID businessId, Pageable pageable);

    Page<ImportJob> findByBusinessIdAndStatusOrderByCreatedDateDesc(
            UUID businessId, ImportStatus status, Pageable pageable);

    /**
     * Moves the job into a working state, but only from a state it is allowed
     * to leave — and tells the caller whether it was this call that moved it.
     *
     * This is what stops an import being committed twice. Two clicks, or two
     * browser tabs, race here rather than in the commit itself: the first
     * update matches and returns 1, the second finds the status already
     * changed and returns 0, and only the winner does any work. Checking the
     * status and then writing it would leave a gap between the two wide enough
     * for both to pass.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImportJob job
               set job.status = :next,
                   job.version = job.version + 1
             where job.id = :jobId
               and job.business.id = :businessId
               and job.status in :allowed
            """)
    int moveToStatus(
            @Param("jobId") UUID jobId,
            @Param("businessId") UUID businessId,
            @Param("next") ImportStatus next,
            @Param("allowed") Collection<ImportStatus> allowed
    );

    /**
     * Releases jobs left mid-flight by a restart.
     *
     * A checking or committing job is only ever advanced by a thread in this
     * process, so after a crash nothing will ever move it and it would sit
     * there claiming to be working forever.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImportJob job
               set job.status = :next,
                   job.failureMessage = :message,
                   job.version = job.version + 1
             where job.status = :stuck
               and job.lastModifiedDate < :olderThan
            """)
    int releaseStuckJobs(
            @Param("stuck") ImportStatus stuck,
            @Param("next") ImportStatus next,
            @Param("message") String message,
            @Param("olderThan") LocalDateTime olderThan
    );
}
