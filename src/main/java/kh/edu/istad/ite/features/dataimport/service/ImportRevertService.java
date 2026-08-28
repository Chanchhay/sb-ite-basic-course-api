package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.repository.ImportJobRepository;
import kh.edu.istad.ite.features.dataimport.repository.ImportRowRepository;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Takes a committed import back out again.
 *
 * Only what the import created. An item it merely updated existed before and
 * keeps its new values — there is no before-image to put back, and inventing
 * one from the file would be a guess presented as a fact.
 *
 * Not transactional itself, for the same reason the commit is not: it walks
 * items one at a time, each in its own transaction, so an item that cannot go
 * leaves the rest of the undo untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportRevertService {

    private final ImportJobRepository importJobRepository;
    private final ImportRowRepository importRowRepository;
    private final ImportRevertWriter writer;
    private final ImportJobStateService jobStateService;

    public void revert(UUID jobId) {
        ImportJob job = importJobRepository.findById(jobId).orElse(null);

        if (job == null) {
            log.warn("Undo asked for import {}, which is not there", jobId);
            return;
        }

        UUID businessId = job.getBusiness().getId();

        try {
            RevertTotals totals = removeWhatWasCreated(businessId, jobId);
            jobStateService.finishRevert(jobId, totals, null);
        } catch (RuntimeException e) {
            log.error("Undoing import {} failed", jobId, e);
            jobStateService.finishRevert(
                    jobId,
                    new RevertTotals(),
                    "The undo stopped part-way. Anything already removed has gone;"
                            + " run it again to finish."
            );
        }
    }

    /** How much of the import is now gone, and what would not go. */
    public static final class RevertTotals {

        private int itemsRemoved;
        private int itemsKept;
        private int itemGroupsRemoved;
        private final List<String> reasonsKept = new ArrayList<>();

        public int itemsRemoved() {
            return itemsRemoved;
        }

        public int itemsKept() {
            return itemsKept;
        }

        public int itemGroupsRemoved() {
            return itemGroupsRemoved;
        }

        public List<String> reasonsKept() {
            return List.copyOf(reasonsKept);
        }

        void noteReason(String reason) {
            if (reason != null && !reasonsKept.contains(reason)) {
                reasonsKept.add(reason);
            }
        }
    }

    private RevertTotals removeWhatWasCreated(UUID businessId, UUID jobId) {
        RevertTotals totals = new RevertTotals();

        List<ImportRow> created = importRowRepository.findByImportJobIdAndStatusInOrderByRowNumberAsc(
                jobId, List.of(ImportRowStatus.CREATED));

        /*
         * One item, however many rows described it. A file listing an option
         * per row created a single shirt across five of them, and the undo has
         * to delete it once — then mark all five.
         */
        Map<UUID, List<UUID>> rowsByItem = new LinkedHashMap<>();
        Set<UUID> createdGroups = new LinkedHashSet<>();

        for (ImportRow row : created) {
            if (row.getCommittedEntityId() != null) {
                rowsByItem
                        .computeIfAbsent(row.getCommittedEntityId(), ignored -> new ArrayList<>())
                        .add(row.getId());
            }
            if (row.getCreatedItemGroupIds() != null) {
                createdGroups.addAll(row.getCreatedItemGroupIds());
            }
        }

        rowsByItem.forEach((itemId, rowIds) -> removeItem(businessId, itemId, rowIds, totals));

        /*
         * Categories last, and only the ones the import invented. A category
         * is only removed once its items have gone, so this could not run
         * first — and one that still holds anything is left alone, which is
         * what the catalogue's own refusal already says.
         */
        for (UUID groupId : createdGroups) {
            removeItemGroup(businessId, groupId, totals);
        }

        return totals;
    }

    /**
     * One item, attempted and then recorded — never both in one transaction.
     *
     * The catch sits out here, outside the transaction that failed, which is
     * the only place it can do any good: a delete that throws has already
     * marked its own transaction rollback-only, and catching it in there would
     * simply lose the marking work as well.
     */
    private void removeItem(UUID businessId, UUID itemId, List<UUID> rowIds, RevertTotals totals) {
        /*
         * Already gone — deleted by hand before the undo reached it. That is
         * the outcome the undo wanted, so the rows are marked as though it had
         * done the deleting itself.
         */
        if (writer.itemIsGone(businessId, itemId)) {
            writer.markReverted(rowIds);
            totals.itemsRemoved++;
            return;
        }

        try {
            writer.deleteImportedItem(businessId, itemId);
        } catch (RuntimeException e) {
            /*
             * Almost always an item with orders against it. A sale is the
             * record of something that really happened, so the item stays and
             * its row goes on saying it was created — which is still true.
             */
            log.info("Undo left item {} in place: {}", itemId, e.getMessage());
            totals.itemsKept++;
            totals.noteReason(readable(e));
            return;
        }

        writer.markReverted(rowIds);
        totals.itemsRemoved++;
    }

    /**
     * A category the import invented, if nothing has moved into it since.
     *
     * Deliberately quiet about failure. A category that has since been given
     * items or sub-categories is one the shop has adopted, and leaving it is
     * the right answer rather than something to report as a problem.
     */
    private void removeItemGroup(UUID businessId, UUID groupId, RevertTotals totals) {
        if (writer.itemGroupIsGone(businessId, groupId)) {
            return;
        }

        try {
            writer.deleteImportedItemGroup(businessId, groupId);
            totals.itemGroupsRemoved++;
        } catch (RuntimeException e) {
            log.debug("Undo left category {} in place: {}", groupId, e.getMessage());
        }
    }

    private String readable(RuntimeException e) {
        String message = e.getMessage();

        return message == null || message.isBlank() ? "It could not be deleted." : message;
    }
}
