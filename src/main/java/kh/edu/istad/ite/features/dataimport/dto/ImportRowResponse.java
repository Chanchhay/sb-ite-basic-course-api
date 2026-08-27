package kh.edu.istad.ite.features.dataimport.dto;

import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One staged row, for the Check Data table.
 *
 * Both readings of the row travel together — what the file said, and what
 * FluxiBiz made of it — because a message about a price is only convincing
 * next to the number it was read as.
 */
public record ImportRowResponse(
        UUID id,
        int rowNumber,
        ImportRowStatus status,
        Map<String, String> sourceValues,
        Map<String, Object> values,
        List<RowIssue> issues,
        int errorCount,
        int warningCount,
        UUID committedEntityId
) {
}
