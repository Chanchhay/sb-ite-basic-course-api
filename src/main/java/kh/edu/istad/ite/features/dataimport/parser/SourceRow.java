package kh.edu.istad.ite.features.dataimport.parser;

import java.util.Map;

/**
 * One row as it came out of the file, before FluxiBiz has made any sense of it.
 *
 * Deliberately dumb: column heading to text, and the line it was found on.
 * Everything past this point works on rows of this shape, which is what lets a
 * database or an API be added later as a new reader rather than as a second
 * copy of validation and commit.
 *
 * @param rowNumber the line in the file the user would count to, headers
 *                  included — so a message about row 40 points at their row 40
 * @param values    keyed by column heading, exactly as the heading was written
 */
public record SourceRow(int rowNumber, Map<String, String> values) {

    /** A row with nothing on it. Trailing blank lines are extremely common. */
    public boolean isBlank() {
        return values.values().stream()
                .allMatch(value -> value == null || value.isBlank());
    }

    public String value(String column) {
        if (column == null) {
            return null;
        }

        String raw = values.get(column);

        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}
