package kh.edu.istad.ite.features.dataimport.canonical;

import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.ItemType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * The user's column matching, turned the useful way round.
 *
 * Stored on the job as heading → field, because that is the order the matching
 * screen presents and edits it in. Read here as field → heading, because every
 * question the reader asks is "which column holds the price".
 *
 * A field matched to two columns is refused when the matching is saved, so
 * this can safely be a plain map.
 */
public record MappingPlan(
        ImportTargetType targetType,
        Map<ImportField, String> columnByField,
        ImportDuplicateStrategy duplicateStrategy,
        UUID defaultUnitId,
        ItemType defaultItemType
) {

    public static MappingPlan from(ImportJob job) {
        Map<ImportField, String> byField = new EnumMap<>(ImportField.class);

        job.getColumnMappings().forEach((column, fieldName) -> {
            if (fieldName == null || fieldName.isBlank()) {
                return;
            }

            ImportField field = ImportField.valueOf(fieldName);
            if (field.appliesTo(job.getTargetType())) {
                byField.put(field, column);
            }
        });

        return new MappingPlan(
                job.getTargetType(),
                byField,
                job.getDuplicateStrategy(),
                job.getDefaultUnitId(),
                job.getDefaultItemType()
        );
    }

    public String columnFor(ImportField field) {
        return columnByField.get(field);
    }

    public boolean isMapped(ImportField field) {
        return columnByField.containsKey(field);
    }
}
