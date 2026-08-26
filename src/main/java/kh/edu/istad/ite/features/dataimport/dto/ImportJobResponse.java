package kh.edu.istad.ite.features.dataimport.dto;

import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportSourceType;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.ItemType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One import, as the dashboard sees it.
 *
 * Carries no storage key and no bucket name: where the file physically sits is
 * this server's business, and the job id is the only handle a client needs.
 */
public record ImportJobResponse(
        UUID id,
        ImportTargetType targetType,
        ImportSourceType sourceType,
        ImportStatus status,
        String fileName,
        long fileSize,
        List<String> sourceColumns,
        Map<String, String> columnMappings,
        ImportDuplicateStrategy duplicateStrategy,
        UUID defaultUnitId,
        ItemType defaultItemType,
        int totalRows,
        int validRows,
        int invalidRows,
        int duplicateRows,
        int createdRows,
        int updatedRows,
        int skippedRows,
        int failedRows,
        int createdItemGroups,
        int createdStockEntries,
        String startedBy,
        LocalDateTime uploadedAt,
        LocalDateTime validationStartedAt,
        LocalDateTime validationCompletedAt,
        LocalDateTime commitStartedAt,
        LocalDateTime commitCompletedAt,
        String failureMessage,
        /** Whether the shop is allowed to press Import right now. */
        boolean committable
) {
}
