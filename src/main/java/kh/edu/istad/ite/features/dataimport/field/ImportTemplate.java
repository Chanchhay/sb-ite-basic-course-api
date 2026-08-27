package kh.edu.istad.ite.features.dataimport.field;

import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

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

    /** The columns offered for one kind of import, in the order they appear. */
    public static List<ImportField> columnsFor(ImportTargetType targetType) {
        return switch (targetType) {
            case ITEM_GROUP -> List.of(
                    ImportField.NAME,
                    ImportField.PARENT_GROUP,
                    ImportField.NOTE
            );
            case ITEM -> List.of(
                    ImportField.NAME,
                    ImportField.SKU,
                    ImportField.BARCODE,
                    ImportField.ITEM_GROUP,
                    ImportField.UNIT,
                    ImportField.ITEM_TYPE,
                    ImportField.PRICE,
                    ImportField.COST_PRICE,
                    ImportField.OPENING_STOCK,
                    ImportField.LOW_STOCK_LEVEL,
                    ImportField.STATUS
            );
            case OPENING_STOCK -> List.of(
                    ImportField.SKU,
                    ImportField.NAME,
                    ImportField.OPENING_STOCK,
                    ImportField.COST_PRICE
            );
        };
    }

    /**
     * A couple of filled-in rows, so the shape of each column is obvious.
     *
     * Deliberately ordinary shop data rather than "value 1, value 2": someone
     * looking at this needs to see that the price column wants 2.50 and not
     * "$2.50", and that the category is a name rather than a code.
     *
     * The item type and status are written the way a person would write them
     * rather than as the enum values they become. Both are read without regard
     * to case or wording, and a sample file that SHOUTS at the shop suggests a
     * strictness that is not really there.
     */
    private static List<List<String>> sampleRowsFor(ImportTargetType targetType) {
        return switch (targetType) {
            case ITEM_GROUP -> List.of(
                    List.of("Beverages", "", "Everything we pour"),
                    List.of("Coffee", "Beverages", "")
            );
            case ITEM -> List.of(
                    List.of("Espresso", "ESP-001", "8850001001", "Coffee", "Piece",
                            "Physical", "2.50", "0.60", "250", "50", "Active"),
                    List.of("Iced Latte", "LAT-002", "8850001002", "Coffee", "Piece",
                            "Physical", "3.75", "0.90", "185", "30", "Active")
            );
            case OPENING_STOCK -> List.of(
                    List.of("ESP-001", "Espresso", "250", "0.60"),
                    List.of("LAT-002", "Iced Latte", "185", "0.90")
            );
        };
    }

    /**
     * The template as a CSV file.
     *
     * Written with a byte-order mark. Without one, Excel on Windows opens a
     * UTF-8 CSV as the local codepage and turns every accented name into
     * mojibake — and a shop's first sight of this feature should not be their
     * own language mangled.
     */
    public static String csvFor(ImportTargetType targetType) {
        List<ImportField> columns = columnsFor(targetType);
        StringWriter out = new StringWriter();

        out.write('﻿');

        CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setHeader(columns.stream().map(ImportField::getLabel).toArray(String[]::new))
                .get();

        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (List<String> row : sampleRowsFor(targetType)) {
                printer.printRecord(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return out.toString();
    }

    /** What the downloaded file is called. */
    public static String fileNameFor(ImportTargetType targetType) {
        return switch (targetType) {
            case ITEM_GROUP -> "fluxibiz-categories-template.csv";
            case ITEM -> "fluxibiz-items-template.csv";
            case OPENING_STOCK -> "fluxibiz-opening-stock-template.csv";
        };
    }
}
