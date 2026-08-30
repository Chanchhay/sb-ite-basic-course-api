package kh.edu.istad.ite.features.migration.resolve;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.FieldResolutionSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing the customer owns, gathered from however many files describe it.
 *
 * This is what the migration works on once the files have been joined: the
 * product row, plus the quantity that was only in the stock export, plus the
 * price that was only in the spreadsheet, all in FluxiBiz's own words with
 * each value still able to say where it came from.
 *
 * Values are claimed rather than overwritten. The first file to supply a field
 * keeps it, so the priority order — what the record itself said, then what a
 * joined file said, then what a rule settled — falls out of the order things
 * are offered, and no later step can quietly replace a value the customer
 * actually gave us.
 *
 * @param rowNumber the line of the main source file, so every message
 *                  downstream can point at something the operator can open
 */
public record ResolvedRecord(int rowNumber, Map<ImportField, FieldValue> fields) {

    public static ResolvedRecord empty(int rowNumber) {
        return new ResolvedRecord(rowNumber, new LinkedHashMap<>());
    }

    /**
     * Offers a value, which is kept only if nothing better already answered.
     *
     * The one way values get in. Blank is not an answer — an empty cell in a
     * joined file must not stop a rule from supplying the field later.
     */
    public void offer(ImportField field, FieldValue value) {
        if (value == null || value.value() == null || value.value().isBlank()) {
            return;
        }

        fields.putIfAbsent(field, value);
    }

    /** Replaces whatever is there. For a decision that is meant to overrule. */
    public void override(ImportField field, FieldValue value) {
        if (value == null || value.value() == null || value.value().isBlank()) {
            return;
        }

        fields.put(field, value);
    }

    public String get(ImportField field) {
        FieldValue found = fields.get(field);

        return found == null ? null : found.value();
    }

    public FieldValue provenanceOf(ImportField field) {
        return fields.get(field);
    }

    public boolean has(ImportField field) {
        return fields.containsKey(field);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /** How many of this record's fields came from each kind of answer. */
    public void countBy(Map<FieldResolutionSource, Integer> into) {
        fields.values().forEach(value -> into.merge(value.resolution(), 1, Integer::sum));
    }
}
