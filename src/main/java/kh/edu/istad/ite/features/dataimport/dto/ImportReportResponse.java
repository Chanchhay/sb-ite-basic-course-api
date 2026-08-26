package kh.edu.istad.ite.features.dataimport.dto;

import kh.edu.istad.ite.shared.enums.ImportStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What an import did, kept for as long as the import history is.
 *
 * The record a shop comes back to weeks later, when something in the catalogue
 * looks wrong and the question is what the migration actually put there.
 */
public record ImportReportResponse(
        UUID importId,
        String fileName,
        ImportTargetType targetType,
        ImportStatus status,
        String startedBy,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int totalRows,
        int createdRows,
        int updatedRows,
        int skippedRows,
        int failedRows,
        int invalidRows,
        int itemGroupsCreated,
        int openingStockRecorded,
        String failureMessage,
        /** The most common problems, worst first, so a long file reads quickly. */
        List<ImportErrorSummary> errorSummary
) {

    /**
     * @param code    the machine-readable reason, for grouping
     * @param message one representative message; the rest read the same
     * @param rows    how many rows were refused for this reason
     */
    public record ImportErrorSummary(
            String field,
            String code,
            String message,
            long rows
    ) {
    }
}
