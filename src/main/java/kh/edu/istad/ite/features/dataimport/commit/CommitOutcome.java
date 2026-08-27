package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.shared.enums.ImportRowStatus;

import java.util.UUID;

/**
 * What became of one row when the import actually ran.
 *
 * @param entityId      the item or category this row became or updated
 * @param stockEntryId  the opening balance it posted, when it posted one
 * @param itemGroupCreated whether a category had to be created for it, so the
 *                         report can say how many appeared
 */
public record CommitOutcome(
        ImportRowStatus status,
        UUID entityId,
        UUID stockEntryId,
        boolean itemGroupCreated,
        String failureMessage
) {

    public static CommitOutcome created(UUID entityId, UUID stockEntryId, boolean itemGroupCreated) {
        return new CommitOutcome(ImportRowStatus.CREATED, entityId, stockEntryId, itemGroupCreated, null);
    }

    public static CommitOutcome updated(UUID entityId, UUID stockEntryId, boolean itemGroupCreated) {
        return new CommitOutcome(ImportRowStatus.UPDATED, entityId, stockEntryId, itemGroupCreated, null);
    }

    public static CommitOutcome skipped(UUID entityId) {
        return new CommitOutcome(ImportRowStatus.SKIPPED, entityId, null, false, null);
    }

    public static CommitOutcome failed(String message) {
        return new CommitOutcome(ImportRowStatus.FAILED, null, null, false, message);
    }
}
