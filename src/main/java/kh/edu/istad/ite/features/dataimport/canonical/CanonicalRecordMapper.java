package kh.edu.istad.ite.features.dataimport.canonical;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Source Row → Field Mapping → Canonical Import Record.
 *
 * The last step that knows a file was involved. Everything it produces is
 * described in FluxiBiz's own terms, and nothing after it can tell whether the
 * row arrived as a comma-separated line or a spreadsheet cell.
 */
@Component
public class CanonicalRecordMapper {

    /**
     * One row, read.
     *
     * @param record what the row means, or null if too little of it could be
     *               read to say
     * @param issues everything that could not be read, per field
     */
    public record MappedRow(ImportRecord record, List<RowIssue> issues) {
    }

    public MappedRow map(SourceRow row, MappingPlan plan) {
        RowReader reader = new RowReader(row, plan);

        ImportRecord record = switch (plan.targetType()) {
            case ITEM_GROUP -> readItemGroup(reader);
            case ITEM -> readItem(reader, plan);
            case OPENING_STOCK -> readOpeningStock(reader);
        };

        return new MappedRow(record, List.copyOf(reader.issues()));
    }

    private ItemGroupImportRecord readItemGroup(RowReader reader) {
        return new ItemGroupImportRecord(
                reader.text(ImportField.NAME, 150),
                reader.text(ImportField.NOTE, 255),
                reader.text(ImportField.PARENT_GROUP, 150)
        );
    }

    private ItemImportRecord readItem(RowReader reader, MappingPlan plan) {
        /*
         * Absent or unrecognised, the type falls back to the choice made for
         * the whole file, and to Physical if none was made — which is what a
         * shop importing a shelf of goods means anyway. The reader has already
         * left a warning on the row when it was a word rather than a blank, so
         * the fallback is visible without being fatal.
         */
        ItemType itemType = reader.itemType(ImportField.ITEM_TYPE);

        if (itemType == null) {
            itemType = plan.defaultItemType() == null ? ItemType.PHYSICAL : plan.defaultItemType();
        }

        BigDecimal openingStock = plan.isMapped(ImportField.OPENING_STOCK)
                ? reader.number(ImportField.OPENING_STOCK)
                : null;

        ItemStatus status = reader.status(ImportField.STATUS);

        return new ItemImportRecord(
                reader.text(ImportField.NAME, 200),
                reader.text(ImportField.SKU, 100),
                reader.text(ImportField.BARCODE, 100),
                reader.text(ImportField.ITEM_GROUP, 150),
                reader.text(ImportField.UNIT, 100),
                itemType,
                reader.number(ImportField.PRICE),
                reader.number(ImportField.COST_PRICE),
                reader.text(ImportField.DESCRIPTION),
                reader.text(ImportField.BADGE, 40),
                reader.flag(ImportField.TRACK_INVENTORY),
                reader.integer(ImportField.LOW_STOCK_LEVEL),
                status == null ? ItemStatus.ACTIVE : status,
                openingStock,
                reader.text(ImportField.OPTION_GROUP_KEY, 200),
                RowOptions.of(
                        reader.text(ImportField.OPTION_1_NAME),
                        reader.text(ImportField.OPTION_1_VALUE, 150),
                        reader.text(ImportField.OPTION_2_NAME),
                        reader.text(ImportField.OPTION_2_VALUE, 150)
                ),
                reader.imageUrl(ImportField.IMAGE_URL)
        );
    }

    private OpeningStockImportRecord readOpeningStock(RowReader reader) {
        return new OpeningStockImportRecord(
                reader.text(ImportField.SKU, 100),
                reader.text(ImportField.BARCODE, 100),
                reader.text(ImportField.NAME, 200),
                reader.number(ImportField.OPENING_STOCK),
                reader.number(ImportField.COST_PRICE)
        );
    }
}
