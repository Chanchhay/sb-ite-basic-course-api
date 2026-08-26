package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.dataimport.canonical.CanonicalRecordMapper;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.commit.CommitOutcome;
import kh.edu.istad.ite.features.dataimport.commit.ImportCommitterRegistry;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes one row, in a transaction of its own.
 *
 * A transaction per row rather than one around the whole import, and that is a
 * deliberate trade. Ten thousand rows in one transaction means a single
 * unforeseen refusal on row nine thousand throws away everything that worked,
 * and holds locks across the catalogue for as long as it takes — during
 * trading hours, on a shop that is still selling.
 *
 * What is given up is all-or-nothing. What replaces it is a report that says
 * exactly which rows went in and which did not, and rows that are safe to run
 * again: a committed row is marked as committed and skipped on a second pass,
 * so a resumed import adds nothing twice.
 *
 * Rows are refused before they get here, so a failure at this point means
 * something changed since the file was checked — an item deleted, a unit
 * withdrawn — which is exactly the case worth reporting rather than hiding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportRowCommitService {

    private final CanonicalRecordMapper recordMapper;
    private final ImportCommitterRegistry committerRegistry;
    private final ImportRowRepository importRowRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommitOutcome commitRow(ImportJob job, ImportRow row, MappingPlan plan) {
        if (row.isCommitted()) {
            // Already in. A resumed commit passes over it rather than through it.
            return new CommitOutcome(row.getStatus(), row.getCommittedEntityId(), null, false, null);
        }

        CommitOutcome outcome = runCommitter(job, row, plan);

        row.setStatus(outcome.status());
        row.setCommittedEntityId(outcome.entityId());
        row.setCommittedStockEntryId(outcome.stockEntryId());

        if (outcome.failureMessage() != null) {
            row.setIssues(withFailure(row, outcome.failureMessage()));
        }

        importRowRepository.save(row);

        return outcome;
    }

    private CommitOutcome runCommitter(ImportJob job, ImportRow row, MappingPlan plan) {
        try {
            SourceRow sourceRow = new SourceRow(row.getRowNumber(), row.getRawData());
            var mapped = recordMapper.map(sourceRow, plan);

            if (mapped.record() == null) {
                return CommitOutcome.failed("This row could not be read.");
            }

            return committerRegistry
                    .forTarget(job.getTargetType())
                    .commit(job, mapped.record(), row.getCommittedEntityId(), plan);
        } catch (ResponseStatusException e) {
            /*
             * The catalogue or the ledger refused it. Their wording is written
             * for a shopkeeper already, so it is passed straight through.
             */
            return CommitOutcome.failed(e.getReason() == null ? "This row was refused." : e.getReason());
        } catch (RuntimeException e) {
            log.warn("Import {} row {} failed", job.getId(), row.getRowNumber(), e);
            return CommitOutcome.failed("Something went wrong importing this row.");
        }
    }

    /**
     * Keeps the warnings checking raised and adds why the write failed.
     *
     * Keyed by code so a retried row does not collect the same complaint
     * twice over.
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
