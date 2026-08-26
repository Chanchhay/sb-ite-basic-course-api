package kh.edu.istad.ite.features.dataimport.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import kh.edu.istad.ite.shared.enums.ImportIssueSeverity;

/**
 * One thing wrong with — or worth saying about — one row.
 *
 * {@code field} names the FluxiBiz field rather than the source column, so the
 * message survives the user re-matching their columns. {@code code} is for the
 * screen to group and filter on; {@code message} is what the shopkeeper reads,
 * and is written to be actionable on its own.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RowIssue(
        String field,
        String code,
        String message,
        ImportIssueSeverity severity
) {

    public static RowIssue error(String field, String code, String message) {
        return new RowIssue(field, code, message, ImportIssueSeverity.ERROR);
    }

    public static RowIssue warning(String field, String code, String message) {
        return new RowIssue(field, code, message, ImportIssueSeverity.WARNING);
    }

    /**
     * Whether this stops the row being imported.
     *
     * Ignored when this is written to the database, and it has to be. These
     * are stored as JSON on the staged row, and Jackson reads a no-argument
     * {@code isX()} as a property to write — so without this it saved an
     * {@code "error"} field that the record's own constructor then refused to
     * read back, and every staged row failed on the round-trip Hibernate makes
     * when it flushes. {@code RowIssueJsonTest} keeps that from returning.
     */
    @JsonIgnore
    public boolean isError() {
        return severity == ImportIssueSeverity.ERROR;
    }
}
