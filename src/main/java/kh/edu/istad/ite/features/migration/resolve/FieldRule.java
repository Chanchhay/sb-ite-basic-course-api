package kh.edu.istad.ite.features.migration.resolve;

import kh.edu.istad.ite.features.dataimport.field.ImportField;

import java.util.Locale;

/**
 * A decision an operator made once, and which rows it covers.
 *
 * The scope is the whole point. "Every item with no unit is counted in pieces"
 * and "every item in Services with no unit is counted in services" are both
 * reasonable and mean very different things, and a resolution that could only
 * say "all of them" would force an operator to accept the coarser of the two
 * or edit rows by hand.
 *
 * @param scope      what the rule reaches — everything, or one category, or
 *                   the rows whose item type is a given one
 * @param scopeValue the category or item type the scope names, where it names one
 * @param value      what to write, in the same words the prepared workbook uses
 * @param rule       how this is remembered afterwards, in an operator's words
 */
public record FieldRule(
        ImportField field,
        Scope scope,
        String scopeValue,
        String value,
        String rule
) {

    public enum Scope {

        /** Every row that still has nothing for this field. */
        ALL,

        /** Only rows filed under one category. */
        CATEGORY,

        /** Only rows of one item type — the physical ones, typically. */
        ITEM_TYPE
    }

    /** Whether this rule has anything to say about a given row. */
    public boolean covers(String category, String itemType) {
        return switch (scope) {
            case ALL -> true;
            case CATEGORY -> matches(scopeValue, category);
            case ITEM_TYPE -> matches(scopeValue, itemType);
        };
    }

    private boolean matches(String expected, String actual) {
        return expected != null
                && actual != null
                && expected.trim().toLowerCase(Locale.ROOT)
                        .equals(actual.trim().toLowerCase(Locale.ROOT));
    }
}
