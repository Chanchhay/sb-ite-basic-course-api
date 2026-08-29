package kh.edu.istad.ite.features.migration.resolve;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.FieldResolutionSource;

import java.util.List;
import java.util.Map;

/**
 * What every field of a whole migration ended up resolving to.
 *
 * One line per field rather than per row, because that is the size of thing a
 * person can act on. "2,400 items have no unit" is a decision; two thousand
 * four hundred identical questions are a morning wasted and a screen of
 * answers given by reflex.
 *
 * @param resolvedBy how many values came from each kind of answer, which is
 *                   what the prepared-data summary reports as percentages
 */
public record MissingFieldReport(
        List<FieldStatus> fields,
        Map<FieldResolutionSource, Integer> resolvedBy
) {

    /**
     * @param missing    rows still with nothing for this field
     * @param blocking   whether those rows stop the migration
     * @param samples    a few real values, or names, so an operator can judge
     */
    public record FieldStatus(
            ImportField field,
            String label,
            MissingFieldBehaviour behaviour,
            int filled,
            int missing,
            boolean blocking,
            String suggestion,
            String question,
            List<String> samples,
            Map<FieldResolutionSource, Integer> resolvedBy
    ) {

        /** Whether this needs a person before the migration can go on. */
        public boolean needsAttention() {
            return blocking && missing > 0;
        }
    }
}
