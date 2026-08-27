package kh.edu.istad.ite.features.dataimport.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything the Match Columns step needs, in one read.
 *
 * @param suggestions  the automatic matches, column heading to field name.
 *                     Filled into the screen's dropdowns and freely
 *                     overridable — they are a head start, not a decision.
 * @param sampleRows   the first rows of the file, so the user can see at a
 *                     glance whether their matching lines up with the data
 * @param requiresUnit whether this kind of import needs a unit, and so whether
 *                     the screen should offer one for the whole file
 */
public record ImportColumnsResponse(
        List<String> sourceColumns,
        List<ImportFieldResponse> targetFields,
        Map<String, String> suggestions,
        Map<String, String> currentMappings,
        List<Map<String, String>> sampleRows,
        boolean requiresUnit
) {
}
