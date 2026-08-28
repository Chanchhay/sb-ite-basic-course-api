package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.commit.GroupCommitOutcome;
import kh.edu.istad.ite.features.dataimport.commit.UnitImportCommitter;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportJobRepository;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the slow half of an import: checking a file, and later writing the
 * rows that passed.
 *
 * Both run on a background thread with the shop's screen watching from a
 * distance, because neither finishes inside the time a browser will wait.
 * Neither claims the job — the service that owns it has already made the
 * status change that settles which thread is doing this work — so by the time
 * either method runs, it is alone.
 *
 * Neither method is transactional, and that is deliberate. The work itself is
 * transactional one layer down — staging in {@link ImportStagingService}, each
 * committed row in {@link ImportRowCommitService} — while every write that
 * records *what happened* goes through {@link ImportJobStateService} in a
 * transaction of its own. A failure can therefore roll back the work without
 * also rolling back the record of the failure, which is what would otherwise
 * leave a job claiming to be checking long after the thread checking it had
 * given up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportProcessingService {

    /**
     * As many rows as one file may carry.
     *
     * A limit rather than a promise of unlimited scale: the workbook reader
     * holds a sheet in memory, and a shop with more history than this is
     * better served splitting the file than by waiting on an import that might
     * exhaust the server.
     */
    public static final int MAX_ROWS = 20_000;

    private final ImportJobRepository importJobRepository;
    private final ImportRowRepository importRowRepository;
    private final ImportStagingService stagingService;
    private final ImportRowCommitService rowCommitService;
    private final ImportJobStateService jobStateService;
    private final UnitImportCommitter unitCommitter;

    // --- checking ------------------------------------------------------------------

    /**
     * Reads the file again from storage, stages every row, and judges it.
     *
     * From the file rather than from whatever a previous run staged: the user
     * may have changed the column matching since, and rows judged under the
     * old matching would otherwise survive into the new answer.
     */
    public void validate(UUID jobId) {
        jobStateService.markValidationStarted(jobId);

        try {
            ImportTotals totals = stagingService.stage(jobId, MAX_ROWS);

            if (totals.total() > MAX_ROWS) {
                jobStateService.failValidation(
                        jobId,
                        "This file has more than " + MAX_ROWS
                                + " rows. Please split it into smaller files."
                );
                return;
            }

            if (totals.total() == 0) {
                jobStateService.failValidation(jobId, "There were no rows to import in this file.");
                return;
            }

            jobStateService.finishValidation(jobId, totals);
        } catch (RuntimeException e) {
            log.error("Import {} could not be checked", jobId, e);
            jobStateService.failValidation(jobId, readableFailure(e));
        }
    }

    // --- committing ----------------------------------------------------------------

    /**
     * Writes the rows that passed, one transaction at a time.
     *
     * Each row opens and closes its own, so a refusal on one leaves the rest
     * exactly as they were, and the counters always describe what really
     * happened rather than what was attempted.
     */
    public void commit(UUID jobId) {
        ImportTotals totals = new ImportTotals();

        jobStateService.markCommitStarted(jobId);

        try {
            ImportJob job = importJobRepository.findById(jobId).orElseThrow();
            MappingPlan plan = MappingPlan.from(job);

            /*
             * Units first, and before a single item is written.
             *
             * Everything downstream is measured in one: an item cannot be
             * created without a unit, and stock cannot be counted without an
             * item. Doing this after the rows would not fail loudly — it would
             * fail as a whole file refused one row at a time for a unit sitting
             * in the same workbook.
             */
            UnitImportCommitter.UnitCommitOutcome units =
                    unitCommitter.commitUnits(job.getBusiness().getId(), job.getDeclaredUnits());

            totals.addUnitsCreated(units.created());

            List<ImportRow> rows = importRowRepository.findByImportJobIdAndStatusInOrderByRowNumberAsc(
                    jobId,
                    List.of(ImportRowStatus.VALID, ImportRowStatus.DUPLICATE)
            );

            for (List<ImportRow> group : groupByItem(rows)) {
                tally(totals, rowCommitService.commitGroup(job, group, plan));
            }

            jobStateService.finishCommit(jobId, totals, null);
        } catch (RuntimeException e) {
            log.error("Import {} stopped part-way through", jobId, e);
            jobStateService.finishCommit(jobId, totals, readableFailure(e));
        }
    }

    /**
     * The rows gathered into the items they describe, in file order.
     *
     * A file listing one row per option takes several rows to describe one
     * item, and the catalogue will only accept those options together. Rows
     * with no group of their own stand alone, which is every row of an ordinary
     * item file.
     */
    private List<List<ImportRow>> groupByItem(List<ImportRow> rows) {
        Map<String, List<ImportRow>> groups = new LinkedHashMap<>();

        for (ImportRow row : rows) {
            String key = row.getGroupKey() == null
                    ? "row:" + row.getId()
                    : "group:" + row.getGroupKey();

            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        return List.copyOf(groups.values());
    }

    /**
     * Counts what was made, not how many rows went into making it.
     *
     * Five rows describing one shirt created one item, and a shop told it
     * created five would go looking for four that do not exist.
     */
    private void tally(ImportTotals totals, GroupCommitOutcome outcome) {
        switch (outcome.status()) {
            case CREATED -> totals.created++;
            case UPDATED -> totals.updated++;
            case SKIPPED -> totals.skipped++;
            default -> totals.failed++;
        }

        if (outcome.itemGroupCreated()) {
            totals.itemGroups++;
        }

        totals.stockEntries += outcome.stockEntryByRow().size();
    }

    /**
     * Something a shopkeeper can act on, rather than a stack trace.
     *
     * A refusal that came from the catalogue, the ledger or a file reader is
     * already written for them and passes straight through. Anything else is
     * deliberately vague to the user and logged in full above, because what it
     * would say is about our internals rather than about their file.
     */
    private String readableFailure(RuntimeException e) {
        if (e instanceof ResponseStatusException refused && refused.getReason() != null) {
            return refused.getReason();
        }

        return "Something went wrong reading this file. Please try again, or contact support"
                + " if it keeps happening.";
    }
}
