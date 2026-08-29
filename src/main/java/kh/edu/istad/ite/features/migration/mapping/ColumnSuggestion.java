package kh.edu.istad.ite.features.migration.mapping;

import kh.edu.istad.ite.features.dataimport.field.ImportField;

/**
 * What one source column probably is, and how sure we are.
 *
 * The confidence is shown rather than acted on silently. An operator who can
 * see that "cat" was matched to Category at 71% because the heading is a known
 * short form, while "prd_desc" reached 96% on its samples too, can trust the
 * first and check the second — which is the whole point of showing a number
 * instead of a tick.
 *
 * @param reason why, in words an operator can weigh — never a score alone
 */
public record ColumnSuggestion(
        String sourceColumn,
        ImportField target,
        double confidence,
        String reason
) {

    /** Certain enough to fill in for them. */
    public static final double HIGH = 0.90;

    /** Worth offering, but they should look. */
    public static final double MEDIUM = 0.65;

    public boolean isHigh() {
        return confidence >= HIGH;
    }

    public boolean isWorthOffering() {
        return confidence >= MEDIUM;
    }
}
