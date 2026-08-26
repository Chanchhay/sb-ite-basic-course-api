package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.features.dataimport.canonical.ImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.ItemImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.ItemType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ItemImportValidator implements ImportRowValidator {

    @Override
    public ImportTargetType targetType() {
        return ImportTargetType.ITEM;
    }

    @Override
    public RowVerdict validate(
            ImportRecord record,
            int rowNumber,
            ValidationContext context,
            MappingPlan plan
    ) {
        ItemImportRecord item = (ItemImportRecord) record;
        List<RowIssue> issues = new ArrayList<>();

        requireName(item, issues);
        requireItemGroup(item, context, issues);
        requireUnit(item, plan, context, issues);
        validatePrices(item, issues);
        validateLowStockLevel(item, issues);

        boolean tracksStock = resolveTracksStock(item);
        validateOpeningStock(item, tracksStock, issues);

        if (issues.stream().anyMatch(RowIssue::isError)) {
            return RowVerdict.invalid(issues);
        }

        RowVerdict duplicate = findDuplicate(item, rowNumber, context, plan, issues);
        if (duplicate != null) {
            return duplicate;
        }

        claim(item, rowNumber, context);

        return RowVerdict.valid(issues);
    }

    private void requireName(ItemImportRecord item, List<RowIssue> issues) {
        if (item.name() == null) {
            issues.add(RowIssue.error(ImportField.NAME.name(), "MISSING_NAME", "An item needs a name."));
        }
    }

    /**
     * A category is not optional — an item cannot be filed or found without
     * one — but it does not have to exist yet. A name this shop has never used
     * becomes a new category when the import runs, which is what makes a
     * first migration into an empty catalogue possible at all.
     */
    private void requireItemGroup(
            ItemImportRecord item,
            ValidationContext context,
            List<RowIssue> issues
    ) {
        if (item.itemGroupName() == null) {
            issues.add(RowIssue.error(
                    ImportField.ITEM_GROUP.name(),
                    "MISSING_ITEM_GROUP",
                    "An item needs a category."
            ));
            return;
        }

        if (!context.hasItemGroup(item.itemGroupName())
                && !context.isItemGroupPlanned(item.itemGroupName())) {
            context.planItemGroup(item.itemGroupName());
            issues.add(RowIssue.warning(
                    ImportField.ITEM_GROUP.name(),
                    "ITEM_GROUP_WILL_BE_CREATED",
                    "The category \"" + item.itemGroupName() + "\" will be created."
            ));
        }
    }

    /**
     * The unit comes from the row when the file has a column for it, and from
     * the one choice made for the whole file when it does not.
     *
     * Units are never invented from a name. The list a shop picks from carries
     * conversions and a measurement category behind it, so a "Ctn" conjured
     * out of a spreadsheet would be a unit that converts to nothing.
     */
    private void requireUnit(
            ItemImportRecord item,
            MappingPlan plan,
            ValidationContext context,
            List<RowIssue> issues
    ) {
        if (item.unitName() != null) {
            if (context.findUnitId(item.unitName()) == null) {
                issues.add(RowIssue.error(
                        ImportField.UNIT.name(),
                        "UNKNOWN_UNIT",
                        "\"" + item.unitName() + "\" is not one of your units. Add it under Units first,"
                                + " or choose a unit for the whole file."
                ));
            }
            return;
        }

        if (plan.defaultUnitId() == null) {
            issues.add(RowIssue.error(
                    ImportField.UNIT.name(),
                    "MISSING_UNIT",
                    "This item has no unit. Match a unit column, or choose one for the whole file."
            ));
        }
    }

    private void validatePrices(ItemImportRecord item, List<RowIssue> issues) {
        rejectNegative(item.price(), ImportField.PRICE, issues);
        rejectNegative(item.compareAtPrice(), ImportField.COMPARE_AT_PRICE, issues);
        rejectNegative(item.costPrice(), ImportField.COST_PRICE, issues);
    }

    private void rejectNegative(BigDecimal value, ImportField field, List<RowIssue> issues) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            issues.add(RowIssue.error(
                    field.name(),
                    "NEGATIVE_AMOUNT",
                    field.getLabel() + " cannot be less than zero."
            ));
        }
    }

    private void validateLowStockLevel(ItemImportRecord item, List<RowIssue> issues) {
        if (item.lowStockLevel() != null && item.lowStockLevel() < 0) {
            issues.add(RowIssue.error(
                    ImportField.LOW_STOCK_LEVEL.name(),
                    "NEGATIVE_AMOUNT",
                    "Low stock level cannot be less than zero."
            ));
        }
    }

    /**
     * Whether this item will count its stock, worked out the same way the
     * catalogue works it out: what the row said, and failing that, whether it
     * is something physical.
     */
    private boolean resolveTracksStock(ItemImportRecord item) {
        if (item.trackInventory() != null) {
            return item.trackInventory();
        }

        return item.itemType() == ItemType.PHYSICAL;
    }

    /**
     * A starting quantity is only meaningful on something that is counted.
     *
     * A service has no shelf and a download never runs out, so a quantity
     * against either is refused rather than quietly dropped — a file that
     * carries one is a file whose columns have probably been matched wrongly,
     * and saying so is more use than importing half of what it meant.
     */
    private void validateOpeningStock(
            ItemImportRecord item,
            boolean tracksStock,
            List<RowIssue> issues
    ) {
        if (!item.hasOpeningStock()) {
            return;
        }

        BigDecimal quantity = item.openingStock();

        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            issues.add(RowIssue.error(
                    ImportField.OPENING_STOCK.name(),
                    "NEGATIVE_QUANTITY",
                    "Opening stock cannot be less than zero."
            ));
            return;
        }

        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        if (!tracksStock) {
            String reason = item.itemType() == ItemType.PHYSICAL
                    ? "this item is set not to track stock"
                    : "a " + item.itemType().name().toLowerCase() + " item does not hold stock";

            issues.add(RowIssue.error(
                    ImportField.OPENING_STOCK.name(),
                    "STOCK_NOT_TRACKED",
                    "Opening stock of " + quantity.toPlainString() + " cannot be recorded because " + reason + "."
            ));
            return;
        }

        if (item.costPrice() == null) {
            issues.add(RowIssue.warning(
                    ImportField.COST_PRICE.name(),
                    "COST_ASSUMED_ZERO",
                    "No cost price given, so this opening stock will be valued at zero."
            ));
        }
    }

    /**
     * Whether this row is something the shop already has.
     *
     * Matched on the identifiers that are meant to be unique — SKU, barcode,
     * and the name, which the catalogue enforces one per shop. A row whose
     * identifiers point at two different items is refused rather than guessed
     * at: updating either one would be the wrong answer half the time.
     */
    private RowVerdict findDuplicate(
            ItemImportRecord item,
            int rowNumber,
            ValidationContext context,
            MappingPlan plan,
            List<RowIssue> issues
    ) {
        RowVerdict inFile = findDuplicateWithinFile(item, context, issues);
        if (inFile != null) {
            return inFile;
        }

        Set<ExistingItem> matches = new LinkedHashSet<>();
        addIfFound(matches, context.findItemBySku(item.sku()));
        addIfFound(matches, context.findItemByBarcode(item.barcode()));
        addIfFound(matches, context.findItemByName(item.name()));

        if (matches.isEmpty()) {
            return null;
        }

        if (matches.size() > 1) {
            issues.add(RowIssue.error(
                    ImportField.SKU.name(),
                    "AMBIGUOUS_MATCH",
                    "This row matches more than one item you already have. "
                            + "Check its SKU, barcode and name refer to the same thing."
            ));
            return RowVerdict.invalid(issues);
        }

        ExistingItem existing = matches.iterator().next();
        claim(item, rowNumber, context);

        String action = plan.duplicateStrategy() == ImportDuplicateStrategy.UPDATE_EXISTING
                ? "It will be updated."
                : "It will be skipped.";

        issues.add(RowIssue.warning(
                ImportField.NAME.name(),
                "ALREADY_EXISTS",
                "You already have \"" + existing.name() + "\". " + action
        ));

        return RowVerdict.duplicate(issues, existing.id());
    }

    private RowVerdict findDuplicateWithinFile(
            ItemImportRecord item,
            ValidationContext context,
            List<RowIssue> issues
    ) {
        Integer byName = context.rowThatTookName(item.name());
        if (byName != null) {
            return duplicateInFile(ImportField.NAME, "name", byName, issues);
        }

        Integer bySku = context.rowThatTookSku(item.sku());
        if (bySku != null) {
            return duplicateInFile(ImportField.SKU, "SKU", bySku, issues);
        }

        Integer byBarcode = context.rowThatTookBarcode(item.barcode());
        if (byBarcode != null) {
            return duplicateInFile(ImportField.BARCODE, "barcode", byBarcode, issues);
        }

        return null;
    }

    private RowVerdict duplicateInFile(
            ImportField field,
            String what,
            int otherRow,
            List<RowIssue> issues
    ) {
        issues.add(RowIssue.warning(
                field.name(),
                "DUPLICATE_IN_FILE",
                "Row " + otherRow + " of this file already uses this " + what + "."
        ));

        return RowVerdict.duplicate(issues, null);
    }

    private void addIfFound(Set<ExistingItem> matches, ExistingItem candidate) {
        if (candidate != null) {
            matches.add(candidate);
        }
    }

    private void claim(ItemImportRecord item, int rowNumber, ValidationContext context) {
        context.claimName(item.name(), rowNumber);
        context.claimSku(item.sku(), rowNumber);
        context.claimBarcode(item.barcode(), rowNumber);
        context.planItem(item.sku(), item.barcode(), item.name());
    }
}
