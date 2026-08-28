package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.commit.GroupCommitOutcome;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Commits one item, however many rows describe it.
 *
 * One item rather than one row, because a file may take several rows to
 * describe one thing: a shirt in five sizes is five rows and one item, and the
 * catalogue takes an item's options as a set it replaces wholesale — so adding
 * them a row at a time would leave each row erasing the one before it. A file
 * of plain items is simply the case where every group is one row long.
 *
 * Nothing here is transactional, deliberately. The write has a transaction of
 * its own and is allowed to fail; recording that it failed gets another. Doing
 * both in one is what turns a single refused item into a whole import reported
 * as unreadable — the catalogue marks the shared transaction rollback-only, and
 * the note explaining the refusal is rolled back along with the refusal.
 *
 * A transaction per item rather than one around the whole import is a
 * deliberate trade. Ten thousand rows in one transaction means a single
 * unforeseen refusal on row nine thousand throws away everything that worked,
 * and holds locks across the catalogue for as long as it takes — during trading
 * hours, on a shop that is still selling. What is given up is all-or-nothing;
 * what replaces it is a report saying exactly what went in, and rows that are
 * safe to run again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportRowCommitService {

    private final ImportGroupWriter groupWriter;

    /**
     * @param rows one item's rows, in file order
     */
    public GroupCommitOutcome commitGroup(ImportJob job, List<ImportRow> rows, MappingPlan plan) {
        ImportRow head = rows.getFirst();

        if (rows.stream().allMatch(ImportRow::isCommitted)) {
            // Already in. A resumed commit passes over them rather than through.
            return GroupCommitOutcome.of(head.getStatus(), head.getCommittedEntityId(), List.of(), Map.of());
        }

        GroupCommitOutcome outcome = attempt(job, rows, plan);

        try {
            groupWriter.record(rows.stream().map(ImportRow::getId).toList(), outcome);
        } catch (RuntimeException e) {
            // The item may well have gone in; only the note about it failed.
            log.error("Import {} could not record the outcome of the group at row {}",
                    job.getId(), head.getRowNumber(), e);
        }

        return outcome;
    }

    private GroupCommitOutcome attempt(ImportJob job, List<ImportRow> rows, MappingPlan plan) {
        try {
            return groupWriter.write(job, rows, plan);
        } catch (ResponseStatusException e) {
            /*
             * The catalogue or the ledger refused it. Their wording is written
             * for a shopkeeper already, so it is passed straight through.
             */
            return GroupCommitOutcome.failed(
                    e.getReason() == null ? "This row was refused." : e.getReason());
        } catch (RuntimeException e) {
            log.error("Import {} could not commit the group at row {}",
                    job.getId(), rows.getFirst().getRowNumber(), e);

            return GroupCommitOutcome.failed(readable(e));
        }
    }

    /**
     * Something the shop can act on.
     *
     * A refusal that reached us wrapped — the catalogue throwing inside a
     * transaction Spring then rolls back — still has its own wording somewhere
     * in the chain, and that is far more use than the wrapper's.
     */
    private String readable(RuntimeException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResponseStatusException refused && refused.getReason() != null) {
                return refused.getReason();
            }

            if (cause.getCause() == cause) {
                break;
            }
        }

        return "This row could not be imported.";
    }
}
