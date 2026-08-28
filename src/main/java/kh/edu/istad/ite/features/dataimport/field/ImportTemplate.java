package kh.edu.istad.ite.features.dataimport.field;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.parser.XlsxSourceFileParser;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The blank file a shop can start from.
 *
 * Its headings are the fields' own labels rather than a list written out by
 * hand, so a downloaded template is matched perfectly by the automatic column
 * matching — the shop fills in their rows, uploads, and every column is
 * already pointed at the right place. {@code ImportTemplateTest} holds that
 * property, because a sample file whose own headings we failed to recognise
 * would undermine the whole step.
 *
 * Not every field is offered. A template carrying all sixteen columns an item
 * can have is a worse starting point than one carrying the nine a migration
 * actually uses — the rest can be added by anyone who wants them, and the
 * matching screen will still find them.
 */
public final class ImportTemplate {

    private ImportTemplate() {
    }

    /**
     * The template as a CSV file.
     *
     * Written with a byte-order mark. Without one, Excel on Windows opens a
     * UTF-8 CSV as the local codepage and turns every accented name into
     * mojibake — and a shop's first sight of this feature should not be their
     * own language mangled.
     */
    public static String csvFor(ImportSample sample) {
        List<ImportField> columns = sample.getColumns();
        StringWriter out = new StringWriter();

        out.write('\ufeff');

        CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setHeader(columns.stream().map(ImportField::getLabel).toArray(String[]::new))
                .get();

        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (List<String> row : sample.getRows()) {
                printer.printRecord(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return out.toString();
    }

    public static String csvFor(ImportTargetType targetType) {
        return csvFor(ImportSample.defaultFor(targetType));
    }

    /**
     * One sample as an Excel workbook.
     *
     * Excel rather than CSV because that is what a shop is looking at when they
     * come to do this — a spreadsheet, not a text file — and because a CSV
     * opened by double-clicking still asks Windows users questions about
     * separators and encodings that have nothing to do with their catalogue.
     * Uploading a CSV works exactly as well; this is only what we hand out.
     *
     * Everything is written as text. A workbook that decided "8850001001" was
     * a number would hand back "8.85E+09" the moment someone saved it, and a
     * barcode is a label rather than a quantity.
     */
    public static byte[] xlsxFor(ImportSample sample) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writeInstructions(workbook, sample);

            Sheet sheet = workbook.createSheet(sample.getLabel());
            List<ImportField> columns = sample.getColumns();

            CellStyle heading = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            heading.setFont(bold);

            Row headings = sheet.createRow(0);

            for (int column = 0; column < columns.size(); column++) {
                Cell cell = headings.createCell(column, CellType.STRING);
                cell.setCellValue(columns.get(column).getLabel());
                cell.setCellStyle(heading);
            }

            for (int index = 0; index < sample.getRows().size(); index++) {
                List<String> values = sample.getRows().get(index);
                Row row = sheet.createRow(index + 1);

                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column, CellType.STRING).setCellValue(values.get(column));
                }
            }

            // So the headings are readable without anyone dragging a column edge.
            for (int column = 0; column < columns.size(); column++) {
                sheet.autoSizeColumn(column);
            }

            sheet.createFreezePane(0, 1);

            writeUnits(workbook, sample);

            workbook.write(out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    /**
     * The sheet that explains the workbook to whoever opens it.
     *
     * First, so it is what they see. The reader skips it by name, along with
     * the Units sheet — see {@code XlsxSourceFileParser} — so a file handed
     * straight back to us still imports from the sheet that holds the rows.
     */
    private static void writeInstructions(XSSFWorkbook workbook, ImportSample sample) {
        Sheet sheet = workbook.createSheet(XlsxSourceFileParser.INSTRUCTIONS_SHEET);

        CellStyle heading = workbook.createCellStyle();
        Font bold = workbook.createFont();
        bold.setBold(true);
        heading.setFont(bold);

        List<String> lines = List.of(
                sample.getLabel(),
                sample.getDescription(),
                "",
                "How to use this file",
                "1. Replace the example rows on the \"" + sample.getLabel() + "\" sheet with your own.",
                "2. Leave a cell empty when it does not apply. Do not write 0 or N/A.",
                "3. Every item needs a Unit. Track Stock says whether its quantity is counted —"
                        + " they are separate things.",
                "4. If you count in something we do not have yet, add it to the Units sheet and"
                        + " the import will create it.",
                "5. Upload the file. Nothing is added until you have seen what will happen.",
                "",
                "Units",
                "A unit's Type must be MASS, VOLUME or COUNT. We never guess it —"
                        + " a unit we cannot place is refused rather than assumed.",
                "",
                "Keep the sheet names as they are. You may add columns; unmatched ones are ignored."
        );

        for (int index = 0; index < lines.size(); index++) {
            Cell cell = sheet.createRow(index).createCell(0, CellType.STRING);
            cell.setCellValue(lines.get(index));

            if (index == 0 || lines.get(index).equals("How to use this file")
                    || lines.get(index).equals("Units")) {
                cell.setCellStyle(heading);
            }
        }

        sheet.setColumnWidth(0, 120 * 256);
    }

    /**
     * The sheet a shop declares its own units on.
     *
     * Written for every sample, even those whose rows name no unit, so the
     * shape is one thing rather than something that appears and disappears.
     */
    private static void writeUnits(XSSFWorkbook workbook, ImportSample sample) {
        Sheet sheet = workbook.createSheet(XlsxSourceFileParser.UNITS_SHEET);

        CellStyle heading = workbook.createCellStyle();
        Font bold = workbook.createFont();
        bold.setBold(true);
        heading.setFont(bold);

        List<String> headings = List.of("Name", "Short Symbol", "Type", "Note");
        Row first = sheet.createRow(0);

        for (int column = 0; column < headings.size(); column++) {
            Cell cell = first.createCell(column, CellType.STRING);
            cell.setCellValue(headings.get(column));
            cell.setCellStyle(heading);
        }

        List<DeclaredUnit> units = sample.getUnits();

        for (int index = 0; index < units.size(); index++) {
            DeclaredUnit unit = units.get(index);
            Row row = sheet.createRow(index + 1);

            row.createCell(0, CellType.STRING).setCellValue(unit.name());
            row.createCell(1, CellType.STRING).setCellValue(unit.symbol() == null ? "" : unit.symbol());
            row.createCell(2, CellType.STRING).setCellValue(unit.category().name());
            row.createCell(3, CellType.STRING).setCellValue(unit.note() == null ? "" : unit.note());
        }

        for (int column = 0; column < headings.size(); column++) {
            sheet.autoSizeColumn(column);
        }

        sheet.createFreezePane(0, 1);
    }

    /** What the downloaded file is called. */
    public static String fileNameFor(ImportSample sample) {
        return sample.getFileName();
    }

    public static String fileNameFor(ImportTargetType targetType) {
        return fileNameFor(ImportSample.defaultFor(targetType));
    }
}
