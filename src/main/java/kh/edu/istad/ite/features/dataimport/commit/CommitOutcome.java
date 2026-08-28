package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.shared.enums.ImportRowStatus;

import java.util.List;
import java.util.UUID;

/**
 * What became of one row when the import actually ran.
 *
 * @param entityId      the item or category this row became or updated
 * @param stockEntryId  the opening balance it posted, when it posted one
 * @param createdItemGroupIds the categories that had to be created for it —
 *                            usually none, one when the file named a new
 *                            category, two when it named a new parent as
 *                            well. Kept so undoing the import can take them
 *                            away again, and counted so the report can say
 *                            how many appeared.
 */
public record CommitOutcome(
        ImportRowStatus status,
        UUID entityId,
        UUID stockEntryId,
        List<UUID> createdItemGroupIds,
        String failureMessage
) {

    public CommitOutcome {
        createdItemGroupIds = createdItemGroupIds == null ? List.of() : List.copyOf(createdItemGroupIds);
    }

    public boolean itemGroupCreated() {
        return !createdItemGroupIds.isEmpty();
    }

    public static CommitOutcome created(UUID entityId, UUID stockEntryId, List<UUID> createdItemGroupIds) {
        return new CommitOutcome(ImportRowStatus.CREATED, entityId, stockEntryId, createdItemGroupIds, null);
    }

    public static CommitOutcome updated(UUID entityId, UUID stockEntryId, List<UUID> createdItemGroupIds) {
        return new CommitOutcome(ImportRowStatus.UPDATED, entityId, stockEntryId, createdItemGroupIds, null);
    }

    public static CommitOutcome skipped(UUID entityId) {
        return new CommitOutcome(ImportRowStatus.SKIPPED, entityId, null, List.of(), null);
    }

    public static CommitOutcome failed(String message) {
        return new CommitOutcome(ImportRowStatus.FAILED, null, null, List.of(), message);
    }
}
