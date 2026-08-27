package kh.edu.istad.ite.features.dataimport.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ItemType;

import java.util.Map;
import java.util.UUID;

/**
 * The user's answers on the Match Columns step.
 *
 * @param mappings        column heading to field name. A heading left out, or
 *                        mapped to null, is a column the import ignores —
 *                        which is normal: exports carry plenty that FluxiBiz
 *                        has no place for.
 * @param defaultUnitId   the unit for every imported item, for the many files
 *                        that have no column for it
 * @param defaultItemType what to treat rows as when the file does not say
 */
public record ImportMappingRequest(
        @NotNull(message = "mappings cannot be null")
        Map<String, String> mappings,

        @NotNull(message = "duplicateStrategy cannot be null")
        ImportDuplicateStrategy duplicateStrategy,

        UUID defaultUnitId,

        ItemType defaultItemType
) {
}
