package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.canonical.OpeningStockImportRecord;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpeningStockImportValidator implements ImportRowValidator {

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.OPENING_STOCK;
    }

    @Override
    public RowVerdict validate(
            ImportRecord record,
            int rowNumber,
            ValidationContext context,
            MappingPlan plan
    ) {
        OpeningStockImportRecord stock = (OpeningStockImportRecord) record;
        List<RowIssue> issues = new ArrayList<>();

        if (!hasIdentifier(stock)) {
            issues.add(RowIssue.error(
                    ImportField.SKU.name(),
                    "MISSING_IDENTIFIER",
                    "This row does not say which item it is for. It needs a SKU, a barcode or a name."
            ));
            return RowVerdict.invalid(issues);
        }

        validateQuantity(stock, issues);

        if (issues.stream().anyMatch(RowIssue::isError)) {
            return RowVerdict.invalid(issues);
        }

        Integer takenBy = firstClaimOf(stock, context);
        if (takenBy != null) {
            issues.add(RowIssue.warning(
                    ImportField.SKU.name(),
                    "DUPLICATE_IN_FILE",
                    "Row " + takenBy + " of this file already sets a quantity for this item."
            ));
            return RowVerdict.duplicate(issues, null);
        }

        claim(stock, rowNumber, context);

        ExistingItem item = context.findItem(stock.sku(), stock.barcode(), stock.itemName());

        if (item == null) {
            /*
             * An opening-stock file on its own can only refer to items that
             * are already here. When the quantities ride along with an item
             * import instead, the item may be a few rows above this one, and
             * the context knows about it because the item validator said so.
             */
            if (context.isItemPlanned(stock.sku(), stock.barcode(), stock.itemName())) {
                return RowVerdict.valid(issues);
            }

            issues.add(RowIssue.error(
                    ImportField.SKU.name(),
                    "ITEM_NOT_FOUND",
                    "No item found for \"" + identifierOf(stock) + "\". Import your items first."
            ));
            return RowVerdict.invalid(issues);
        }

        return judgeAgainstExisting(stock, item, issues);
    }

    private RowVerdict judgeAgainstExisting(
            OpeningStockImportRecord stock,
            ExistingItem item,
            List<RowIssue> issues
    ) {
        if (!item.trackInventory()) {
            issues.add(RowIssue.error(
                    ImportField.OPENING_STOCK.name(),
                    "STOCK_NOT_TRACKED",
                    "\"" + item.name() + "\" does not track stock, so it cannot be given a quantity."
            ));
            return RowVerdict.invalid(issues);
        }

        if (item.soldInOptions()) {
            issues.add(RowIssue.error(
                    ImportField.OPENING_STOCK.name(),
                    "SOLD_IN_OPTIONS",
                    "\"" + item.name() + "\" is sold in options, and each option is counted separately."
                            + " Set its quantities on the item itself."
            ));
            return RowVerdict.invalid(issues);
        }

        /*
         * An opening balance is the first entry in an item's ledger and there
         * can only ever be one. Importing the same file twice must therefore
         * leave the second one alone rather than add to what is on the shelf,
         * which is why opening stock has no update strategy at all.
         */
        if (item.hasStockHistory()) {
            issues.add(RowIssue.warning(
                    ImportField.OPENING_STOCK.name(),
                    "STOCK_ALREADY_RECORDED",
                    "\"" + item.name() + "\" already has stock recorded, so this row will be skipped."
            ));
            return RowVerdict.duplicate(issues, item.id());
        }

        if (stock.unitCost() == null) {
            issues.add(RowIssue.warning(
                    ImportField.COST_PRICE.name(),
                    "COST_ASSUMED_ZERO",
                    "No cost price given, so this stock will be valued at zero."
            ));
        }

        return RowVerdict.valid(issues);
    }

    private void validateQuantity(OpeningStockImportRecord stock, List<RowIssue> issues) {
        if (stock.quantity() == null) {
            issues.add(RowIssue.error(
                    ImportField.OPENING_STOCK.name(),
                    "MISSING_QUANTITY",
                    "This row has no quantity."
            ));
            return;
        }

        if (stock.quantity().compareTo(BigDecimal.ZERO) < 0) {
            issues.add(RowIssue.error(
                    ImportField.OPENING_STOCK.name(),
                    "NEGATIVE_QUANTITY",
                    "Opening stock cannot be less than zero."
            ));
        }

        if (stock.unitCost() != null && stock.unitCost().compareTo(BigDecimal.ZERO) < 0) {
            issues.add(RowIssue.error(
                    ImportField.COST_PRICE.name(),
                    "NEGATIVE_AMOUNT",
                    "Cost price cannot be less than zero."
            ));
        }
    }

    private boolean hasIdentifier(OpeningStockImportRecord stock) {
        return stock.sku() != null || stock.barcode() != null || stock.itemName() != null;
    }

    private String identifierOf(OpeningStockImportRecord stock) {
        return stock.externalId();
    }

    private Integer firstClaimOf(OpeningStockImportRecord stock, ValidationContext context) {
        Integer bySku = context.rowThatTookSku(stock.sku());
        if (bySku != null) {
            return bySku;
        }

        Integer byBarcode = context.rowThatTookBarcode(stock.barcode());

        return byBarcode != null ? byBarcode : context.rowThatTookName(stock.itemName());
    }

    private void claim(OpeningStockImportRecord stock, int rowNumber, ValidationContext context) {
        context.claimSku(stock.sku(), rowNumber);
        context.claimBarcode(stock.barcode(), rowNumber);
        context.claimName(stock.itemName(), rowNumber);
    }
}
