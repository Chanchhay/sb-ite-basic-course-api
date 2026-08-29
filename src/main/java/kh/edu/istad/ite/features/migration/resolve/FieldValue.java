package kh.edu.istad.ite.features.migration.resolve;

import kh.edu.istad.ite.shared.enums.FieldResolutionSource;

/**
 * One prepared value, and the account of how it got there.
 *
 * The provenance travels with the value rather than being reconstructed
 * afterwards, because afterwards is too late: once several files have been
 * joined and a handful of rules applied, "why is this item counted in cans?"
 * has no answer that does not involve re-running everything and guessing.
 * Carrying it costs a few words per field and turns that question into a
 * lookup.
 *
 * @param value          what will be written into the prepared workbook
 * @param resolution     how it was arrived at, in order of how much was assumed
 * @param sourceFile     the file it came from, where one did
 * @param sourceColumn   the heading it came from, where one did
 * @param sourceRow      the line of that file, so an operator can go and look
 * @param rule           what settled it, when nothing in the file did — the
 *                       name of a derivation, or the operator's own decision
 */
public record FieldValue(
        String value,
        FieldResolutionSource resolution,
        String sourceFile,
        String sourceColumn,
        Integer sourceRow,
        String rule
) {

    public static FieldValue direct(String value, String file, String column, int row) {
        return new FieldValue(value, FieldResolutionSource.DIRECT_SOURCE, file, column, row, null);
    }

    public static FieldValue joined(String value, String file, String column, int row) {
        return new FieldValue(value, FieldResolutionSource.JOINED_SOURCE, file, column, row, null);
    }

    public static FieldValue derived(String value, String rule) {
        return new FieldValue(value, FieldResolutionSource.DERIVED, null, null, null, rule);
    }

    public static FieldValue byDefault(String value, String rule) {
        return new FieldValue(value, FieldResolutionSource.MIGRATION_DEFAULT, null, null, null, rule);
    }

    public static FieldValue decided(String value, String rule) {
        return new FieldValue(value, FieldResolutionSource.OPERATOR_RESOLUTION, null, null, null, rule);
    }

    /** Where this came from, said the way an operator would say it. */
    public String describeOrigin() {
        return switch (resolution) {
            case DIRECT_SOURCE, JOINED_SOURCE -> sourceFile + " · " + sourceColumn;
            case DERIVED, MIGRATION_DEFAULT, OPERATOR_RESOLUTION -> rule;
            case UNRESOLVED -> "nothing supplied it";
        };
    }
}
