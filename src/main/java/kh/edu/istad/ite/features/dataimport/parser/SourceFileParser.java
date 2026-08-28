package kh.edu.istad.ite.features.dataimport.parser;

import kh.edu.istad.ite.shared.enums.ImportSourceType;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads one file format into plain rows.
 *
 * The two methods exist because the file is read twice for different reasons.
 * On upload only the headings and a handful of rows are wanted, so the column
 * matching screen has something to show immediately. On checking, every row is
 * wanted, and handed over one at a time rather than returned as a list — a
 * shop's catalogue export is not something to hold twice in memory.
 */
public interface SourceFileParser {

    ImportSourceType sourceType();

    /** Whether this reader handles the given file, judged by its extension. */
    boolean supports(String fileName);

    /**
     * The column headings, plus the first few rows as a sample.
     *
     * @param sampleLimit how many rows to bring back beside the headings
     */
    SourceHeader readHeader(InputStream input, int sampleLimit);

    /**
     * Every row in the file, in order, blank ones already dropped.
     *
     * @param rowLimit stop after this many rows; a file longer than the import
     *                 feature is willing to handle is refused rather than
     *                 silently truncated, and the caller decides which by
     *                 counting what it was given
     */
    void readRows(InputStream input, int rowLimit, Consumer<SourceRow> handler);

    /**
     * The rows of a sheet kept beside the main one, if the format has sheets
     * and this file carries that one.
     *
     * Empty for a CSV, which has no sheets to keep anything beside — and that
     * is an answer rather than a failure: a shop importing a CSV declares no
     * units in it, and is told so where it matters.
     */
    default List<SourceRow> readNamedSheet(InputStream input, String sheetName, int rowLimit) {
        return List.of();
    }

    /**
     * What a file announced about itself before any of it was interpreted.
     *
     * @param columns headings in the order they appear
     * @param sample  the first rows, for the matching preview
     */
    record SourceHeader(List<String> columns, List<SourceRow> sample) {
    }
}
