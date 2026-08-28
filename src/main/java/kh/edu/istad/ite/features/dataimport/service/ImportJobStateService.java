package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.repository.ImportJobRepository;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The small transactional writes that bracket a long-running import.
 *
 * A bean of its own rather than methods on the service that calls them:
 * Spring's transactions are applied by a proxy, and a method calling its own
 * neighbour goes straight past it — so a {@code @Transactional} marked
 * alongside the loop it brackets would silently not be one.
 *
 * Each of these opens and closes a transaction in an instant. That matters
 * during a commit, which deliberately runs outside one so that a refusal on
 * row nine thousand does not take the first eight thousand with it.
 */
@Service
@RequiredArgsConstructor
public class ImportJobStateService {

    private final ImportJobRepository importJobRepository;

    /**
     * Frees jobs left claiming to be working, and says how many there were.
     *
     * A checking job is released to a failed check, which is retryable and
     * costs the shop nothing — no catalogue data was written. A committing job
     * is released to failed, because some of its rows may well have gone in;
     * the staged rows record what each one became, so a second run picks up
     * where the first stopped rather than importing twice.
     */
    @Transactional
    public int releaseInterrupted(
            ImportStatus stuck,
            ImportStatus next,
            String message,
            LocalDateTime olderThan
    ) {
        return importJobRepository.releaseStuckJobs(stuck, next, message, olderThan);
    }

    @Transactional
    public void markValidationStarted(UUID jobId) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();
        job.setValidationStartedAt(LocalDateTime.now());
        job.setValidationCompletedAt(null);
        job.setFailureMessage(null);

        // A fresh check means the last run's outcome no longer describes
        // anything, and a half-updated set of counters is worse than none.
        job.setTotalRows(0);
        job.setValidRows(0);
        job.setInvalidRows(0);
        job.setDuplicateRows(0);
        job.setOpeningStockRows(0);
        job.setEntitiesToCreate(0);
        job.setCreatedRows(0);
        job.setUpdatedRows(0);
        job.setSkippedRows(0);
        job.setFailedRows(0);
        job.setCreatedItemGroups(0);
        job.setCreatedStockEntries(0);

        importJobRepository.save(job);
    }

    @Transactional
    public void finishValidation(UUID jobId, ImportTotals totals) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();

        job.setTotalRows(totals.total());
        job.setValidRows(totals.valid());
        job.setInvalidRows(totals.invalid());
        job.setDuplicateRows(totals.duplicate());
        job.setOpeningStockRows(totals.openingStock());
        job.setEntitiesToCreate(totals.entities());
        job.setValidationCompletedAt(LocalDateTime.now());
        job.setStatus(ImportStatus.READY);

        importJobRepository.save(job);
    }

    /**
     * Records that a check came to nothing, and why.
     *
     * Its own transaction, because the usual reason to call it is that the
     * transaction which did the reading has just been rolled back. Writing the
     * reason inside that same transaction is how a job ends up stuck in a
     * checking state with nothing to show for it — the work is undone and so
     * is the note explaining why.
     */
    @Transactional
    public void failValidation(UUID jobId, String failure) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();

        job.setStatus(ImportStatus.VALIDATION_FAILED);
        job.setFailureMessage(failure);
        job.setValidationCompletedAt(LocalDateTime.now());

        importJobRepository.save(job);
    }

    @Transactional
    public void markCommitStarted(UUID jobId) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();
        job.setCommitStartedAt(LocalDateTime.now());
        job.setFailureMessage(null);
        importJobRepository.save(job);
    }

    /**
     * Records what a commit did, whether or not it reached the end.
     *
     * A run that stopped part-way still writes its counters. The rows that
     * went in are in, and a report that pretended otherwise would send the
     * shop hunting for items that are already there.
     */
    @Transactional
    public void finishCommit(UUID jobId, ImportTotals totals, String failure) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();

        job.setCreatedRows(totals.created());
        job.setUpdatedRows(totals.updated());
        job.setSkippedRows(totals.skipped());
        job.setFailedRows(totals.failed());
        job.setCreatedItemGroups(totals.itemGroups());
        job.setCreatedStockEntries(totals.stockEntries());
        job.setCommitCompletedAt(LocalDateTime.now());
        job.setStatus(failure == null ? ImportStatus.COMMITTED : ImportStatus.FAILED);
        job.setFailureMessage(failure);

        importJobRepository.save(job);
    }

    /**
     * Writes down what an undo managed to remove.
     *
     * The counts are rewritten rather than zeroed: an import whose items are
     * gone but whose updates stand did not create nothing, and a report saying
     * so would be a lie about what happened. What changes is how many of its
     * creations survive.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishRevert(UUID jobId, ImportRevertService.RevertTotals totals, String failure) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();

        job.setCreatedRows(totals.itemsKept());
        int groupsBefore = job.getCreatedItemGroups() == null ? 0 : job.getCreatedItemGroups();
        job.setCreatedItemGroups(Math.max(0, groupsBefore - totals.itemGroupsRemoved()));
        job.setCreatedStockEntries(0);
        job.setRevertedAt(LocalDateTime.now());
        job.setStatus(failure == null ? ImportStatus.REVERTED : ImportStatus.COMMITTED);
        job.setFailureMessage(failure);

        importJobRepository.save(job);
    }
}
