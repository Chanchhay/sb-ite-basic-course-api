package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.shared.enums.ImportRowStatus;

import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * What became of one item, however many rows described it.
 *
 * A file that lists one row per option describes a single shirt across five
 * rows, and the shop should be told it created one item — not five. So the
 * status here is the item's, and it is written onto every row of the group.
 *
 * @param stockEntryByRow the opening balance posted for each row's option,
 *                        keyed by row number, so each row records the entry it
 *                        actually produced
 */
public record GroupCommitOutcome(
        ImportRowStatus status,
        UUID entityId,
        List<UUID> createdItemGroupIds,
        Map<Integer, UUID> stockEntryByRow,
        String failureMessage
) {

    public GroupCommitOutcome {
        createdItemGroupIds = createdItemGroupIds == null ? List.of() : List.copyOf(createdItemGroupIds);
    }

    public boolean itemGroupCreated() {
        return !createdItemGroupIds.isEmpty();
    }

    public static GroupCommitOutcome of(
            ImportRowStatus status,
            UUID entityId,
            List<UUID> createdItemGroupIds,
            Map<Integer, UUID> stockEntryByRow
    ) {
        return new GroupCommitOutcome(status, entityId, createdItemGroupIds, stockEntryByRow, null);
    }

    public static GroupCommitOutcome failed(String message) {
        return new GroupCommitOutcome(ImportRowStatus.FAILED, null, List.of(), Map.of(), message);
    }

    /** From a single-row commit, so both paths report the same way. */
    public static GroupCommitOutcome from(CommitOutcome outcome, int rowNumber) {
        Map<Integer, UUID> stock = outcome.stockEntryId() == null
                ? Map.of()
                : Map.of(rowNumber, outcome.stockEntryId());

        return new GroupCommitOutcome(
                outcome.status(),
                outcome.entityId(),
                outcome.createdItemGroupIds(),
                stock,
                outcome.failureMessage()
        );
    }
}
