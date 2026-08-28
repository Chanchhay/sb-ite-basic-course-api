package kh.edu.istad.ite.features.dataimport.parser;

import kh.edu.istad.ite.shared.enums.ImportSourceType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads the first sheet of an .xlsx workbook.
 *
 * The first sheet only, and its first row is taken as the headings. A shop
 * exporting its catalogue puts it on one sheet; asking which of several to use
 * would be a question most users cannot answer about a file their old system
 * generated.
 *
 * The workbook is read into memory rather than streamed. That is a considered
 * limit, not an oversight: uploads are capped at a size and a row count that
 * keep this comfortable, and the SAX reader POI offers instead costs an
 * afternoon of XML plumbing to save memory this feature does not need. If the
 * caps are ever raised much, this is the class to revisit.
 */
@Component
public class XlsxSourceFileParser implements SourceFileParser {

    /** The sheet our samples explain themselves on. */
    public static final String INSTRUCTIONS_SHEET = "Instructions";

    /** The sheet a shop declares units on, so the import can create them. */
    public static final String UNITS_SHEET = "Units";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public ImportSourceType sourceType() {
        return ImportSourceType.XLSX_UPLOAD;
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".xlsx");
    }

    @Override
    public SourceHeader readHeader(InputStream input, int sampleLimit) {
        try (Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = firstSheet(workbook);
            List<String> columns = columnsOf(sheet);
            List<SourceRow> sample = new ArrayList<>();

            for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                if (sample.size() >= sampleLimit) {
                    break;
                }

                SourceRow row = toRow(sheet.getRow(index), index, columns);
                if (row != null && !row.isBlank()) {
                    sample.add(row);
                }
            }

            return new SourceHeader(columns, sample);
        } catch (IOException | RuntimeException e) {
            throw unreadable(e);
        }
    }

    @Override
    public void readRows(InputStream input, int rowLimit, Consumer<SourceRow> handler) {
        try (Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = firstSheet(workbook);
            List<String> columns = columnsOf(sheet);
            int kept = 0;

            for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                SourceRow row = toRow(sheet.getRow(index), index, columns);
                if (row == null || row.isBlank()) {
                    continue;
                }

                accept(handler, row);

                if (++kept > rowLimit) {
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            throw unreadable(e);
        }
    }

    /**
     * The sheet holding the rows to import.
     *
     * Our own workbooks carry an Instructions sheet to read and a Units sheet
     * to fill in beside the one that matters, so "the first sheet" stopped
     * being the right answer the moment we started shipping them. Sheets we
     * put there ourselves are skipped by name; anything else — every export a
     * shop brings from its old system — falls through to the first sheet
     * exactly as before.
     */
    private Sheet firstSheet(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This workbook has no sheets in it.");
        }

        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);

            if (!isOursToSkip(sheet.getSheetName())) {
                return sheet;
            }
        }

        return workbook.getSheetAt(0);
    }

    /** Sheets we add to our own samples, which never hold the rows to import. */
    private boolean isOursToSkip(String sheetName) {
        String normalized = sheetName == null ? "" : sheetName.trim().toLowerCase();

        return normalized.equals(INSTRUCTIONS_SHEET.toLowerCase())
                || normalized.equals(UNITS_SHEET.toLowerCase());
    }

    @Override
    public List<SourceRow> readNamedSheet(InputStream input, String sheetName, int rowLimit) {
        try (Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                return List.of();
            }

            List<String> columns = columnsOf(sheet);
            List<SourceRow> rows = new ArrayList<>();

            for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                if (rows.size() >= rowLimit) {
                    break;
                }

                SourceRow row = toRow(sheet.getRow(index), index, columns);

                if (row != null) {
                    rows.add(row);
                }
            }

            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> columnsOf(Sheet sheet) {
        Row header = sheet.getRow(sheet.getFirstRowNum());

        if (header == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The first row of the sheet should be the column headings, but it is empty."
            );
        }

        List<String> columns = new ArrayList<>();
        for (int index = 0; index < header.getLastCellNum(); index++) {
            String heading = readCell(header.getCell(index));
            columns.add(heading == null ? "" : heading.trim());
        }

        while (!columns.isEmpty() && columns.getLast().isBlank()) {
            columns.removeLast();
        }

        if (columns.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The first row of the sheet should be the column headings, but it is empty."
            );
        }

        return columns;
    }

    /**
     * @param index the zero-based sheet row, so the row number handed on is
     *              the one the spreadsheet itself puts in the margin
     */
    private SourceRow toRow(Row row, int index, List<String> columns) {
        if (row == null) {
            return null;
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int column = 0; column < columns.size(); column++) {
            values.put(columns.get(column), readCell(row.getCell(column)));
        }

        return new SourceRow(index + 1, values);
    }

    /**
     * A cell as the text the user sees in it.
     *
     * Numbers are the fiddly part. A spreadsheet holds every number as a
     * double, so a barcode typed into a plain cell comes back as
     * 8.850001001E9, and a price of 2.50 as 2.5. Rendering through BigDecimal
     * and dropping only trailing zeros keeps both readable, and keeps a long
     * code from being turned into scientific notation that matches nothing.
     */
    private String readCell(Cell cell) {
        if (cell == null) {
            return null;
        }

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        return switch (type) {
            case STRING -> emptyToNull(cell.getStringCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> readNumeric(cell);
            case BLANK, ERROR, _NONE, FORMULA -> null;
        };
    }

    private String readNumeric(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMAT);
        }

        BigDecimal value = BigDecimal.valueOf(cell.getNumericCellValue());

        return value.stripTrailingZeros().toPlainString();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Hands one row over, keeping whatever the handler throws distinguishable
     * from a problem with the file itself.
     */
    private void accept(Consumer<SourceRow> handler, SourceRow row) {
        try {
            handler.accept(row);
        } catch (RuntimeException e) {
            throw new RowHandlerException(e);
        }
    }

    private RuntimeException unreadable(Exception e) {
        // Not ours to describe: the caller's failure travels back untouched.
        if (e instanceof RowHandlerException handlerFailure) {
            return handlerFailure.getCause();
        }

        if (e instanceof ResponseStatusException already) {
            return already;
        }

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "This Excel file could not be read. Please make sure it is a valid .xlsx file and try again.",
                e
        );
    }
}
