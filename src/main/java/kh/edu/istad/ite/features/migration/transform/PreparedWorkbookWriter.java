package kh.edu.istad.ite.features.migration.transform;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.field.ImportSample;
import kh.edu.istad.ite.features.dataimport.parser.XlsxSourceFileParser;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Writes what a migration worked out as one of FluxiBiz's own workbooks.
 *
 * This is the handover, and making it a real file rather than a private
 * arrangement is the point. The importer receives exactly what a shopkeeper
 * would have uploaded — same sheets, same headings, same Units sheet — so
 * there is one way into a catalogue and no second format to keep in step.
 *
 * It also means the operator can download precisely what was handed over and
 * open it. A migration that goes wrong somewhere downstream can be argued
 * about with the file in front of you, rather than by reading logs.
 */
@Component
public class PreparedWorkbookWriter {

    /**
     * Which of the official shapes to write, decided by what the data has.
     *
     * A file whose rows carry options has to go in as options — the same three
     * rows written as plain items would be three shirts rather than one shirt
     * in three sizes.
     */
    public ImportSample shapeFor(ImportTargetType targetType, List<PreparedRow> rows) {
        boolean hasOptions = rows.stream().anyMatch(row ->
                row.get(ImportField.OPTION_GROUP_KEY) != null
                        || row.get(ImportField.OPTION_1_VALUE) != null);

        if (targetType == ImportTargetType.ITEM && hasOptions) {
            return ImportSample.ITEMS_WITH_OPTIONS;
        }

        return ImportSample.defaultFor(targetType);
    }

    public byte[] write(ImportSample shape, List<PreparedRow> rows, List<DeclaredUnit> units) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle heading = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            heading.setFont(bold);

            /*
             * The columns of the official sample, in its order — not the ones
             * this file happens to have filled in. A workbook whose columns
             * came and went with the data would be a second format in all but
             * name, and the importer would have to learn both.
             */
            List<ImportField> columns = shape.getColumns();
            Sheet sheet = workbook.createSheet(shape.getLabel());
            Row headings = sheet.createRow(0);

            for (int column = 0; column < columns.size(); column++) {
                Cell cell = headings.createCell(column, CellType.STRING);
                cell.setCellValue(columns.get(column).getLabel());
                cell.setCellStyle(heading);
            }

            for (int index = 0; index < rows.size(); index++) {
                PreparedRow prepared = rows.get(index);
                Row row = sheet.createRow(index + 1);

                for (int column = 0; column < columns.size(); column++) {
                    String value = prepared.get(columns.get(column));

                    // Empty stays empty. A zero written where a shop left a
                    // blank is a claim they never made.
                    row.createCell(column, CellType.STRING).setCellValue(value == null ? "" : value);
                }
            }

            for (int column = 0; column < columns.size(); column++) {
                sheet.autoSizeColumn(column);
            }

            sheet.createFreezePane(0, 1);

            writeUnits(workbook, heading, units);

            workbook.write(out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The units this file needs, declared so the importer can create them.
     *
     * Including the ones an operator decided the meaning of. That decision was
     * made once, here it becomes a row, and the importer creates the unit
     * before any item that counts in it — which is the existing dependency
     * order, not a new one.
     */
    private void writeUnits(XSSFWorkbook workbook, CellStyle heading, List<DeclaredUnit> units) {
        Sheet sheet = workbook.createSheet(XlsxSourceFileParser.UNITS_SHEET);
        List<String> headings = List.of("Name", "Short Symbol", "Type", "Note");
        Row first = sheet.createRow(0);

        for (int column = 0; column < headings.size(); column++) {
            Cell cell = first.createCell(column, CellType.STRING);
            cell.setCellValue(headings.get(column));
            cell.setCellStyle(heading);
        }

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
    }
}
