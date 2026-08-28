package kh.edu.istad.ite.features.dataimport.field;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.shared.enums.UnitCategory;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

import static kh.edu.istad.ite.features.dataimport.field.ImportField.*;

/**
 * The starting files a shop can download, one per shape a catalogue comes in.
 *
 * There is no single right shape for an items file. A shop selling one thing
 * per line needs a row per item; a shop selling shirts needs a row per size and
 * colour, which looks nothing like it. Offering one file forced both through
 * the same columns, and the columns a shop did not need were the ones they had
 * to work out how to leave out.
 *
 * Every sample is a working file: fill in your own rows, upload it, and the
 * matching screen recognises every column, because the headings are the fields'
 * own labels.
 *
 * @param label       what the shop picks from a list
 * @param description the sentence that tells them whether it is theirs
 */
@Getter
public enum ImportSample {

    /**
     * One row per item, whether or not it is counted.
     *
     * Track Stock is what makes it serve both kinds of shop. A row that says No
     * leaves Unit, Opening Stock and Low Stock Level empty — they mean nothing
     * for a haircut — and the sample shows that rather than explaining it.
     */
    ITEMS(
            ImportTargetType.ITEM,
            "One row per item",
            "The usual shape. Every item needs a unit; Track Stock says whether its quantity"
                    + " is counted. Units you do not have yet go on the Units sheet.",
            "fluxibiz-items.xlsx",
            List.of(NAME, SKU, BARCODE, ITEM_GROUP, PARENT_GROUP, UNIT, ITEM_TYPE, TRACK_INVENTORY,
                    PRICE, COST_PRICE, OPENING_STOCK, LOW_STOCK_LEVEL, STATUS),
            List.of(
                    List.of("Espresso Beans", "ESP-001", "8850001001", "Coffee", "Beverages",
                            "bag", "Physical", "Yes", "12.50", "7.20", "40", "10", "Active"),
                    List.of("Oat Milk", "MLK-002", "8850001002", "Dairy", "",
                            "ctn", "Physical", "Yes", "3.75", "2.10", "24", "6", "Active"),
                    // Not counted: no unit, no quantity, no low-stock warning.
                    // Not counted, but still counted *in* something: every item
                    // needs a unit, whether or not its quantity is tracked.
                    List.of("Gift Wrapping", "SRV-001", "", "Services", "",
                            "svc", "Service", "No", "2.50", "", "", "", "Active"),
                    List.of("Recipe eBook", "DIG-001", "", "Digital", "",
                            "license", "Digital", "No", "9.00", "", "", "", "Active")
            ),
            List.of(
                    new DeclaredUnit("Bag", "bag", UnitCategory.COUNT, null),
                    new DeclaredUnit("Carton", "ctn", UnitCategory.COUNT, null),
                    new DeclaredUnit("Service", "svc", UnitCategory.COUNT, "Used for service items"),
                    new DeclaredUnit("License", "license", UnitCategory.COUNT, "Used for digital items"),
                    new DeclaredUnit("Kilogram", "kg", UnitCategory.MASS, null),
                    new DeclaredUnit("Liter", "L", UnitCategory.VOLUME, null)
            )
    ),

    /**
     * One row per option, several rows per item.
     *
     * The shape people get wrong first, because it does not look like a list of
     * items. Groups By ties an item's rows together; without it these rows would
     * be nine separate items. The last item varies by colour alone, which is why
     * its Option 2 columns are empty — an item need not use both.
     */
    ITEMS_WITH_OPTIONS(
            ImportTargetType.ITEM,
            "One row per option",
            "For items sold in sizes, colours or flavours. Rows sharing a Groups By value become"
                    + " one item — so they share one unit — and each row is a shelf with its own"
                    + " SKU, price and stock.",
            "fluxibiz-items-with-options.xlsx",
            List.of(OPTION_GROUP_KEY, NAME, SKU, ITEM_GROUP, PARENT_GROUP,
                    OPTION_1_NAME, OPTION_1_VALUE, OPTION_2_NAME, OPTION_2_VALUE,
                    UNIT, TRACK_INVENTORY, PRICE, COST_PRICE, OPENING_STOCK, IMAGE_URL),
            List.of(
                    List.of("TSHIRT-01", "Classic Graphic T-Shirt", "TS-S-BLK", "T-Shirts", "Apparel",
                            "Size", "Small", "Color", "Black",
                            "pc", "Yes", "19.99", "5.50", "45",
                            "https://cdn.example.com/img/ts-blk.jpg"),
                    List.of("TSHIRT-01", "Classic Graphic T-Shirt", "TS-M-BLK", "T-Shirts", "Apparel",
                            "Size", "Medium", "Color", "Black",
                            "pc", "Yes", "19.99", "5.50", "60",
                            "https://cdn.example.com/img/ts-blk.jpg"),
                    List.of("TSHIRT-01", "Classic Graphic T-Shirt", "TS-S-RED", "T-Shirts", "Apparel",
                            "Size", "Small", "Color", "Red",
                            "pc", "Yes", "19.99", "5.50", "30",
                            "https://cdn.example.com/img/ts-red.jpg"),
                    // Two axes, neither of them a colour: both join into the option's name.
                    List.of("COFFEE-02", "Caramel Macchiato", "CM-12-WHL", "Coffee", "Beverages",
                            "Size", "12oz (Tall)", "Milk Type", "Whole Milk",
                            "cup", "No", "4.50", "1.20", "",
                            "https://cdn.example.com/img/macchiato.jpg"),
                    List.of("COFFEE-02", "Caramel Macchiato", "CM-16-OAT", "Coffee", "Beverages",
                            "Size", "16oz (Grande)", "Milk Type", "Oat Milk",
                            "cup", "No", "5.75", "1.90", "",
                            "https://cdn.example.com/img/macchiato.jpg"),
                    // One axis only: the colour is the option.
                    List.of("WATCH-03", "ProFit Smartwatch Gen 5", "SW-SLV", "Wearables", "Electronics",
                            "Color", "Silver", "", "",
                            "pc", "Yes", "199.99", "85.00", "15",
                            "https://cdn.example.com/img/sw-slv.jpg"),
                    List.of("WATCH-03", "ProFit Smartwatch Gen 5", "SW-GLD", "Wearables", "Electronics",
                            "Color", "Rose Gold", "", "",
                            "pc", "Yes", "219.99", "95.00", "8",
                            "https://cdn.example.com/img/sw-gld.jpg")
            ),
            List.of(
                    new DeclaredUnit("Piece", "pc", UnitCategory.COUNT, null),
                    new DeclaredUnit("Cup", "cup", UnitCategory.COUNT, null)
            )
    ),

    CATEGORIES(
            ImportTargetType.ITEM_GROUP,
            "Categories",
            "Just the shelves. Name a Parent Category to put one underneath another.",
            "fluxibiz-categories.xlsx",
            List.of(NAME, PARENT_GROUP, NOTE),
            List.of(
                    List.of("Beverages", "", "Everything we pour"),
                    List.of("Coffee", "Beverages", ""),
                    List.of("Services", "", "")
            )
    ),

    OPENING_STOCK_COUNTS(
            ImportTargetType.OPENING_STOCK,
            "Stock counts",
            "Quantities for items already in FluxiBiz, matched by SKU.",
            "fluxibiz-stock-counts.xlsx",
            List.of(SKU, NAME, OPENING_STOCK, COST_PRICE),
            List.of(
                    List.of("ESP-001", "Espresso Beans", "40", "7.20"),
                    List.of("MLK-002", "Oat Milk", "24", "2.10")
            )
    );

    private final ImportTargetType targetType;
    private final String label;
    private final String description;
    private final String fileName;
    private final List<ImportField> columns;
    private final List<List<String>> rows;

    /**
     * The units this sample's rows are counted in, written onto its Units sheet.
     *
     * A sample that referenced units a shop has never heard of would refuse
     * itself on upload, which is a poor advertisement for the feature. These
     * are the same declarations a shop writes for its own: the import creates
     * whichever the catalogue has not got.
     */
    private final List<DeclaredUnit> units;

    ImportSample(
            ImportTargetType targetType,
            String label,
            String description,
            String fileName,
            List<ImportField> columns,
            List<List<String>> rows
    ) {
        this(targetType, label, description, fileName, columns, rows, List.of());
    }

    ImportSample(
            ImportTargetType targetType,
            String label,
            String description,
            String fileName,
            List<ImportField> columns,
            List<List<String>> rows,
            List<DeclaredUnit> units
    ) {
        this.units = units;
        this.targetType = targetType;
        this.label = label;
        this.description = description;
        this.fileName = fileName;
        this.columns = columns;
        this.rows = rows;
    }

    /** The samples worth offering for one kind of import, in the order shown. */
    public static List<ImportSample> forTarget(ImportTargetType targetType) {
        return Arrays.stream(values())
                .filter(sample -> sample.targetType == targetType)
                .toList();
    }

    /** The one to hand over when a shop asked for a kind of import and nothing more. */
    public static ImportSample defaultFor(ImportTargetType targetType) {
        return forTarget(targetType).getFirst();
    }
}
