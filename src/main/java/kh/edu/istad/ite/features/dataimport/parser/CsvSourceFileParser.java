package kh.edu.istad.ite.features.dataimport.parser;

import kh.edu.istad.ite.shared.enums.ImportSourceType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class CsvSourceFileParser implements SourceFileParser {

    /**
     * Excel writes one of these at the start of a CSV it exported as UTF-8.
     * Left in place it becomes part of the first column heading, and the
     * heading then matches nothing — the single most common reason a perfectly
     * good file appears to have an unrecognised first column.
     */
    private static final char BYTE_ORDER_MARK = '﻿';

    @Override
    public ImportSourceType sourceType() {
        return ImportSourceType.CSV_UPLOAD;
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".csv");
    }

    @Override
    public SourceHeader readHeader(InputStream input, int sampleLimit) {
        try (CSVParser parser = open(input)) {
            List<String> columns = columnsOf(parser);
            List<SourceRow> sample = new ArrayList<>();

            for (CSVRecord record : parser) {
                if (sample.size() >= sampleLimit) {
                    break;
                }

                SourceRow row = toRow(record, columns, parser.getCurrentLineNumber());
                if (!row.isBlank()) {
                    sample.add(row);
                }
            }

            return new SourceHeader(columns, sample);
        } catch (IOException e) {
            throw unreadable(e);
        }
    }

    @Override
    public void readRows(InputStream input, int rowLimit, Consumer<SourceRow> handler) {
        try (CSVParser parser = open(input)) {
            List<String> columns = columnsOf(parser);
            int kept = 0;

            for (CSVRecord record : parser) {
                SourceRow row = toRow(record, columns, parser.getCurrentLineNumber());
                if (row.isBlank()) {
                    continue;
                }

                // Only IOException is treated as a bad file below, so anything
                // the handler throws already travels back untouched.
                handler.accept(row);

                if (++kept > rowLimit) {
                    return;
                }
            }
        } catch (IOException e) {
            throw unreadable(e);
        }
    }

    private CSVParser open(InputStream input) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

        CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .setAllowMissingColumnNames(true)
                .get();

        return CSVParser.parse(reader, format);
    }

    private List<String> columnsOf(CSVParser parser) {
        List<String> columns = new ArrayList<>();

        for (String heading : parser.getHeaderNames()) {
            columns.add(clean(heading));
        }

        if (columns.stream().allMatch(String::isBlank)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The first line of the file should be the column headings, but it is empty."
            );
        }

        return columns;
    }

    private String clean(String heading) {
        if (heading == null) {
            return "";
        }

        String cleaned = heading.trim();

        return cleaned.isEmpty() || cleaned.charAt(0) != BYTE_ORDER_MARK
                ? cleaned
                : cleaned.substring(1).trim();
    }

    /**
     * @param lineNumber the physical line the record just ended on, taken from
     *                   the parser rather than counted here.
     *                   <p>
     *                   Counting rows ourselves would drift the moment a file
     *                   had a blank line in the middle of it — the reader skips
     *                   those, the user's spreadsheet does not — and every error
     *                   message after that point would name the wrong row. The
     *                   one case it is not exact is a value with a line break
     *                   inside it, where this names the line the row ended on
     *                   rather than the one it began on; that still lands the
     *                   reader on the right row.
     *                   {@code CsvSourceFileParserTest} pins the behaviour.
     */
    private SourceRow toRow(CSVRecord record, List<String> columns, long lineNumber) {
        Map<String, String> values = new LinkedHashMap<>();

        for (int index = 0; index < columns.size(); index++) {
            String value = index < record.size() ? record.get(index) : null;
            values.put(columns.get(index), value == null ? null : value.trim());
        }

        return new SourceRow((int) lineNumber, values);
    }

    private ResponseStatusException unreadable(IOException e) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "This CSV file could not be read. Please check it opens correctly in a spreadsheet and try again.",
                e
        );
    }
}
