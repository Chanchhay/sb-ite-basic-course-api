package kh.edu.istad.ite.features.migration.transform;

import kh.edu.istad.ite.features.dataimport.field.ImportField;

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
 * @param sourceRowNumber the line of the customer's own file, so every message
 *                        downstream can point back at something they can open
 */
public record PreparedRow(int sourceRowNumber, Map<ImportField, String> values) {

    public static PreparedRow empty(int sourceRowNumber) {
        return new PreparedRow(sourceRowNumber, new LinkedHashMap<>());
    }

    public String get(ImportField field) {
        return values.get(field);
    }

    public void put(ImportField field, String value) {
        if (value != null && !value.isBlank()) {
            values.put(field, value);
        }
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
