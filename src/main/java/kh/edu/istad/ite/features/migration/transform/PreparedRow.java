package kh.edu.istad.ite.features.migration.transform;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.migration.resolve.FieldValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One source row, said in FluxiBiz's words.
 *
 * Deliberately still text. This is what gets written into the official
 * workbook and handed to the importer, so it stays in the form the importer
 * already knows how to read and refuses — turning it into typed values here
 * would mean two things parsing the same data and eventually disagreeing.
 *
 * Each value keeps the account of how it got here. That costs a reference per
 * field and answers the question that is otherwise unanswerable once several
 * files have been joined and a few rules applied: not what this item's unit is,
 * but why it is that.
 *
 * @param sourceRowNumber the line of the customer's own file, so every message
 *                        downstream can point back at something they can open
 * @param provenance      where each value came from, keyed the same as values
 */
public record PreparedRow(
        int sourceRowNumber,
        Map<ImportField, String> values,
        Map<ImportField, FieldValue> provenance
) {

    public static PreparedRow empty(int sourceRowNumber) {
        return new PreparedRow(sourceRowNumber, new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    public String get(ImportField field) {
        return values.get(field);
    }

    /**
     * Sets a value whose origin is already known.
     *
     * The normal way in. The origin carries the value it was resolved with
     * rather than the normalised one, because "the file said SACK" is the
     * useful half of "the file said SACK, which reads as Bag".
     */
    public void put(ImportField field, String value, FieldValue origin) {
        if (value == null || value.isBlank()) {
            return;
        }

        values.put(field, value);

        if (origin != null) {
            provenance.put(field, origin);
        }
    }

    /** Sets a value whose origin nothing recorded. */
    public void put(ImportField field, String value) {
        put(field, value, null);
    }

    public FieldValue originOf(ImportField field) {
        return provenance.get(field);
    }

    public boolean has(ImportField field) {
        String value = values.get(field);

        return value != null && !value.isBlank();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
