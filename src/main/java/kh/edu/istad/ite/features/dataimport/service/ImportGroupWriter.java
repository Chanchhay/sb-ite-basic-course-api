package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.dataimport.canonical.CanonicalRecordMapper;
import kh.edu.istad.ite.features.dataimport.canonical.ItemImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.commit.CommitOutcome;
import kh.edu.istad.ite.features.dataimport.commit.GroupCommitOutcome;
import kh.edu.istad.ite.features.dataimport.commit.ImportCommitterRegistry;
import kh.edu.istad.ite.features.dataimport.commit.ItemImportCommitter;
import kh.edu.istad.ite.features.dataimport.commit.OptionRow;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two transactions a committed item needs: the write, and the record of
 * how it went.
 *
 * They have to be two. The catalogue and the ledger are transactional in their
 * own right, so a refusal from either marks the transaction it was called in
 * as rollback-only — and catching that exception does not un-mark it. Recording
 * the failure in the same transaction therefore succeeds right up until the
 * commit, which throws {@code UnexpectedRollbackException} and takes the whole
 * import down with it: no rows marked, no counts, and a shop told the file
 * could not be read.
 *
 * So {@link #write} does the writing and is allowed to throw, and
 * {@link #record} writes down what happened in a transaction of its own that
 * nothing has poisoned.
 */
@Service
@RequiredArgsConstructor
public class ImportGroupWriter {

    private final CanonicalRecordMapper recordMapper;
    private final ImportCommitterRegistry committerRegistry;
    private final ItemImportCommitter itemImportCommitter;
    private final ImportRowRepository importRowRepository;

    /**
     * Writes one item into the catalogue.
     *
     * Throws on refusal rather than reporting it, so this transaction is rolled
     * back cleanly and the caller is free to record the reason elsewhere.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GroupCommitOutcome write(ImportJob job, List<ImportRow> rows, MappingPlan plan) {
        List<OptionRow> optionRows = readOptionRows(rows, plan);

        if (optionRows != null) {
            return itemImportCommitter.commitOptionGroup(
                    job, optionRows, rows.getFirst().getCommittedEntityId(), plan);
        }

        ImportRow row = rows.getFirst();
        var mapped = recordMapper.map(new SourceRow(row.getRowNumber(), row.getRawData()), plan);

        if (mapped.record() == null) {
            return GroupCommitOutcome.failed("This row could not be read.");
        }

        CommitOutcome outcome = committerRegistry
                .forTarget(job.getTargetType())
                .commit(job, mapped.record(), row.getCommittedEntityId(), plan);

        return GroupCommitOutcome.from(outcome, row.getRowNumber());
    }

    /**
     * Marks the rows with what became of them.
     *
     * Reads them again rather than reusing the ones handed to {@link #write}:
     * that transaction may have been rolled back, which leaves everything it
     * touched detached and its changes gone.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(List<UUID> rowIds, GroupCommitOutcome outcome) {
        List<ImportRow> rows = importRowRepository.findAllById(rowIds);

        for (ImportRow row : rows) {
            row.setStatus(outcome.status());
            row.setCommittedStockEntryId(outcome.stockEntryByRow().get(row.getRowNumber()));

            /*
             * Only a write that succeeded says what the row became. On a
             * failure the row keeps what checking found it matched, because
             * that is what a retry needs in order to update the existing item
             * rather than try to create a second one with the same name — and
             * then fail again for exactly that reason.
             */
            if (outcome.entityId() != null) {
                row.setCommittedEntityId(outcome.entityId());
            }

            /*
             * Written onto the first row of the group only. Several rows can
             * describe one item, but the categories were created once, and
             * recording them against every row would have an undo try to
             * delete the same category five times.
             */
            if (!outcome.createdItemGroupIds().isEmpty()
                    && row.getRowNumber().equals(firstRowNumber(rows))) {
                row.setCreatedItemGroupIds(List.copyOf(outcome.createdItemGroupIds()));
            }

            if (outcome.failureMessage() != null) {
                row.setIssues(withFailure(row, outcome.failureMessage()));
            }
        }

        importRowRepository.saveAll(rows);
    }

    /**
     * The group read as options of one item, or null if it is not one.
     *
     * A single row that names no option is a plain item and takes the ordinary
     * path — which is every row of a file with no option columns at all.
     */
    private List<OptionRow> readOptionRows(List<ImportRow> rows, MappingPlan plan) {
        List<OptionRow> optionRows = new ArrayList<>();

        for (ImportRow row : rows) {
            var mapped = recordMapper.map(new SourceRow(row.getRowNumber(), row.getRawData()), plan);

            if (!(mapped.record() instanceof ItemImportRecord item) || !item.hasOptions()) {
                return null;
            }

            optionRows.add(new OptionRow(row.getRowNumber(), item));
        }

        return optionRows.isEmpty() ? null : optionRows;
    }

    private Integer firstRowNumber(List<ImportRow> rows) {
        return rows.stream()
                .map(ImportRow::getRowNumber)
                .min(Integer::compareTo)
                .orElse(null);
    }

    /**
     * Keeps the warnings checking raised and adds why the write failed.
     *
     * Keyed by code so a retried row does not collect the same complaint twice
     * over.
     */
    private List<RowIssue> withFailure(ImportRow row, String message) {
        Map<String, RowIssue> issues = new LinkedHashMap<>();

        if (row.getIssues() != null) {
            row.getIssues().forEach(issue -> issues.put(issue.code(), issue));
        }

        issues.put("IMPORT_FAILED", RowIssue.error(null, "IMPORT_FAILED", message));

        return List.copyOf(issues.values());
    }
}
