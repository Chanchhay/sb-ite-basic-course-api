package kh.edu.istad.ite.features.dataimport.dto;

import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

import java.util.UUID;

/**
 * What will happen if the shop presses Import — the last screen before
 * anything becomes real.
 *
 * Deliberately phrased as consequences rather than as row states. "1,920 items
 * will be created" is a sentence a shopkeeper can agree or object to; "1,920
 * rows are VALID" is not.
 */
public record ImportPreviewResponse(
        UUID importId,
        ImportTargetType targetType,
        ImportStatus status,
        ImportDuplicateStrategy duplicateStrategy,
        int totalRows,
        int validRows,
        int duplicateRows,
        int invalidRows,
        /** Rows that will bring something new into being. */
        int willCreate,
        /** Rows that will overwrite something that is already there. */
        int willUpdate,
        /** Rows that match something already there and will be left alone. */
        int willSkip,
        /** Rows with errors. They are not imported, and nothing is lost by it. */
        int willFail,
        /** Categories that do not exist yet and will be created along the way. */
        int itemGroupsToCreate,
        /** Items that will be given a starting quantity. */
        int openingStockToRecord,
        boolean committable
) {
}
