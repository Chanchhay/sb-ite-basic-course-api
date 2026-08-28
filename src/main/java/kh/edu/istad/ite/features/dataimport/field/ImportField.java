package kh.edu.istad.ite.features.dataimport.field;

import kh.edu.istad.ite.shared.enums.ImportTargetType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static kh.edu.istad.ite.features.dataimport.field.ImportFieldRequirement.IDENTIFIER;
import static kh.edu.istad.ite.features.dataimport.field.ImportFieldRequirement.OPTIONAL;
import static kh.edu.istad.ite.features.dataimport.field.ImportFieldRequirement.REQUIRED;
import static kh.edu.istad.ite.features.dataimport.field.ImportFieldRequirement.REQUIRED_OR_DEFAULTED;

/**
 * The FluxiBiz fields a source column can be matched to.
 *
 * One list, shared by the three kinds of import, because a name is a name
 * whichever of them is running — what changes is whether it is required, which
 * is what {@link #requirementFor} answers.
 *
 * Only fields the item screens themselves offer are listed. The catalogue
 * carries a few more that nothing in the dashboard edits any more — an item's
 * internal code among them — and offering those here would invite a shop to
 * migrate data into a corner of the database they can never see again.
 *
 * The aliases are the whole of the automatic matching. They are compared
 * against headings flattened to letters and digits only, so PRODUCT_NAME,
 * "Product Name" and product-name all arrive here as productname and all find
 * {@link #NAME}. Suggestions are only ever suggestions: the matching screen
 * fills them in and the user can overrule every one.
 */
public enum ImportField {

    NAME(
            "Name",
            "What the item or category is called.",
            ImportFieldType.TEXT,
            Map.of(
                    ImportTargetType.ITEM_GROUP, REQUIRED,
                    ImportTargetType.ITEM, REQUIRED,
                    ImportTargetType.OPENING_STOCK, IDENTIFIER
            ),
            List.of("name", "itemname", "productname", "title", "description1", "item", "product")
    ),

    SKU(
            "SKU",
            "Your own code for the item.",
            ImportFieldType.TEXT,
            Map.of(
                    ImportTargetType.ITEM, OPTIONAL,
                    ImportTargetType.OPENING_STOCK, IDENTIFIER
            ),
            List.of("sku", "productcode", "itemcode", "stockcode", "articlenumber", "artno", "reference")
    ),

    BARCODE(
            "Barcode",
            "The barcode printed on the item.",
            ImportFieldType.TEXT,
            Map.of(
                    ImportTargetType.ITEM, OPTIONAL,
                    ImportTargetType.OPENING_STOCK, IDENTIFIER
            ),
            List.of("barcode", "ean", "upc", "gtin", "ean13", "scancode")
    ),

    ITEM_GROUP(
            "Category",
            "The category the item is filed under. Categories that do not exist yet are created."
                    + " A whole path — \"Drinks > Coffee\" — is read as a category and its parent.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, REQUIRED),
            List.of("category", "itemgroup", "group", "categoryname", "department", "productcategory",
                    "type1", "subcategory", "subgroup", "subdepartment", "childcategory",
                    "breadcrumbs", "breadcrumb", "categorypath", "categorytree")
    ),

    PARENT_GROUP(
            "Parent Category",
            "The category this one sits under, if any. A category holding sub-categories cannot"
                    + " hold items itself, so this is how a file names both halves at once.",
            ImportFieldType.TEXT,
            Map.of(
                    ImportTargetType.ITEM_GROUP, OPTIONAL,
                    ImportTargetType.ITEM, OPTIONAL
            ),
            List.of("parent", "parentcategory", "parentgroup", "maincategory", "topcategory",
                    "maingroup", "maindepartment")
    ),

    NOTE(
            "Note",
            "A short internal note.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM_GROUP, OPTIONAL),
            List.of("note", "notes", "remark", "remarks", "comment")
    ),

    UNIT(
            "Unit",
            "What the item is counted in — pieces, kilograms, cups.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, REQUIRED_OR_DEFAULTED),
            List.of("unit", "uom", "unitofmeasure", "measure", "baseunit", "unitname")
    ),

    ITEM_TYPE(
            "Item Type",
            "Physical or digital.",
            ImportFieldType.ENUM,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("itemtype", "type", "producttype", "kind")
    ),

    PRICE(
            "Selling Price",
            "What you sell it for.",
            ImportFieldType.MONEY,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("price", "sellingprice", "saleprice", "unitprice", "retailprice", "rate", "amount")
    ),

    COST_PRICE(
            "Cost Price",
            "What one unit cost you. Used to value the stock you start with.",
            ImportFieldType.MONEY,
            Map.of(
                    ImportTargetType.ITEM, OPTIONAL,
                    ImportTargetType.OPENING_STOCK, OPTIONAL
            ),
            List.of("costprice", "cost", "buyingprice", "purchaseprice", "unitcost", "wholesaleprice")
    ),

    OPENING_STOCK(
            "Opening Stock",
            "How many you have on hand right now.",
            ImportFieldType.NUMBER,
            Map.of(
                    ImportTargetType.ITEM, OPTIONAL,
                    ImportTargetType.OPENING_STOCK, REQUIRED
            ),
            List.of("openingstock", "qty", "quantity", "stock", "stockqty", "onhand", "stockonhand",
                    "balance", "quantityonhand", "currentstock")
    ),

    /**
     * What ties several rows together into one item sold in options.
     *
     * A variant export lists one row per option — Small/Black, Small/White,
     * Medium/Black — and repeats the item's own details on every one. This is
     * the column that says which of them belong to the same item. Shops call
     * it a style code, a parent SKU, a handle; whatever it is called, it is
     * the same value on every row of the group.
     */
    OPTION_GROUP_KEY(
            "Groups By",
            "Ties option rows together. Rows sharing this value become one item sold in options.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("groupsby", "groupby", "itemgroupid", "parentsku", "parentid", "parentcode",
                    "styleid", "stylecode", "handle", "groupid", "productid", "parent",
                    "variantgroup", "modelcode")
    ),

    OPTION_1_NAME(
            "Option 1 Name",
            "What the first option is — Size, for instance. The same on every row of an item.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("option1name", "optionname1", "attribute1name", "variant1name", "option1")
    ),

    OPTION_1_VALUE(
            "Option 1 Value",
            "This row's first option — Small.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("option1value", "optionvalue1", "attribute1value", "variant1value", "size")
    ),

    OPTION_2_NAME(
            "Option 2 Name",
            "What the second option is — Color, for instance.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("option2name", "optionname2", "attribute2name", "variant2name", "option2")
    ),

    OPTION_2_VALUE(
            "Option 2 Value",
            "This row's second option — Black.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("option2value", "optionvalue2", "attribute2value", "variant2value", "color", "colour")
    ),

    IMAGE_URL(
            "Image URL",
            "A picture of this item, or of this option. Linked to, not copied, so it has"
                    + " to be an https address the public can reach.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("imageurl", "image", "picture", "photo", "imagelink", "thumbnail")
    ),

    DESCRIPTION(
            "Description",
            "A longer description of the item.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("description", "details", "longdescription", "itemdescription")
    ),

    BADGE(
            "Badge",
            "A short label shown on the item, such as New or Hot.",
            ImportFieldType.TEXT,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("badge", "label", "tag", "flag")
    ),

    TRACK_INVENTORY(
            "Track Stock",
            "Whether FluxiBiz should count this item's stock.",
            ImportFieldType.BOOLEAN,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("trackinventory", "trackstock", "stocktracked", "inventorytracked", "manageinventory")
    ),

    LOW_STOCK_LEVEL(
            "Low Stock Level",
            "The quantity at which the item counts as running low.",
            ImportFieldType.NUMBER,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("lowstocklevel", "reorderlevel", "reorderpoint", "minstock", "minimumstock", "safetystock")
    ),

    STATUS(
            "Status",
            "Whether the item is active.",
            ImportFieldType.ENUM,
            Map.of(ImportTargetType.ITEM, OPTIONAL),
            List.of("status", "active", "itemstatus", "state", "enabled")
    );

    private final String label;
    private final String help;
    private final ImportFieldType type;
    private final Map<ImportTargetType, ImportFieldRequirement> requirements;
    private final List<String> aliases;

    ImportField(
            String label,
            String help,
            ImportFieldType type,
            Map<ImportTargetType, ImportFieldRequirement> requirements,
            List<String> aliases
    ) {
        this.label = label;
        this.help = help;
        this.type = type;
        this.requirements = requirements;
        this.aliases = aliases;
    }

    public String getLabel() {
        return label;
    }

    public String getHelp() {
        return help;
    }

    public ImportFieldType getType() {
        return type;
    }

    /** Null when this field means nothing for that kind of import. */
    public ImportFieldRequirement requirementFor(ImportTargetType targetType) {
        return requirements.get(targetType);
    }

    public boolean appliesTo(ImportTargetType targetType) {
        return requirements.containsKey(targetType);
    }

    /** The fields offered for one kind of import, in the order declared here. */
    public static List<ImportField> forTarget(ImportTargetType targetType) {
        return Arrays.stream(values())
                .filter(field -> field.appliesTo(targetType))
                .toList();
    }

    public static Set<ImportField> requiredFor(ImportTargetType targetType) {
        return forTarget(targetType).stream()
                .filter(field -> field.requirementFor(targetType) == REQUIRED)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<ImportField> identifiersFor(ImportTargetType targetType) {
        return forTarget(targetType).stream()
                .filter(field -> field.requirementFor(targetType) == IDENTIFIER)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The best guess for a column heading, if there is one.
     *
     * An exact alias match only. Fuzzy matching sounds helpful and is not:
     * quietly matching "supplier code" to SKU costs a shop more to discover
     * and undo than leaving the row for them to match themselves.
     */
    public static Optional<ImportField> suggestFor(String heading, ImportTargetType targetType) {
        String normalized = normalize(heading);

        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        return forTarget(targetType).stream()
                .filter(field -> field.aliases.contains(normalized))
                .findFirst();
    }

    /** Headings naming the narrower half of a category pair. */
    private static final Set<String> SUB_CATEGORY_HEADINGS =
            Set.of("subcategory", "subgroup", "subdepartment", "childcategory", "sub");

    /** Headings naming the wider half — main, category or parent, as shops write it. */
    private static final Set<String> TOP_CATEGORY_HEADINGS =
            Set.of("category", "categoryname", "productcategory", "department", "maincategory",
                    "maingroup", "maindepartment", "main", "parentcategory", "parent",
                    "parentgroup", "topcategory", "itemgroup", "group");

    /**
     * Reads a pair of category columns the way the shop means it.
     *
     * A file names the two halves as main and sub, category and sub, or parent
     * and sub — all the same shape. Left to the ordinary matching the wider
     * column would claim the field an item is filed under, and the narrower one,
     * arriving second with that field already taken, would be left unmatched.
     * Every item would then land on the parent, which is the one place the
     * catalogue refuses to keep one.
     *
     * So when both halves are present they are assigned together and the
     * narrower one wins the filing. A file carrying only one of the two never
     * reaches this and is matched normally.
     */
    private static void pairCategoryColumns(
            List<String> headings,
            ImportTargetType targetType,
            Map<String, ImportField> suggestions
    ) {
        if (!ITEM_GROUP.appliesTo(targetType) || !PARENT_GROUP.appliesTo(targetType)) {
            return;
        }

        String sub = firstHeadingIn(headings, SUB_CATEGORY_HEADINGS);
        String top = firstHeadingIn(headings, TOP_CATEGORY_HEADINGS);

        if (sub == null || top == null) {
            return;
        }

        suggestions.put(sub, ITEM_GROUP);
        suggestions.put(top, PARENT_GROUP);
    }

    private static String firstHeadingIn(List<String> headings, Set<String> wanted) {
        return headings.stream()
                .filter(heading -> wanted.contains(normalize(heading)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Every heading's suggestion, with each field claimed at most once.
     *
     * Two columns that both look like the name would otherwise both be matched
     * to it, and the user would have to notice the clash before they could fix
     * it. First heading wins; the second is left unmatched and visible.
     */
    public static Map<String, ImportField> suggestAll(List<String> headings, ImportTargetType targetType) {
        Map<String, ImportField> suggestions = new LinkedHashMap<>();

        pairCategoryColumns(headings, targetType, suggestions);

        for (String heading : headings) {
            suggestFor(heading, targetType)
                    .filter(field -> !suggestions.containsValue(field))
                    .ifPresent(field -> suggestions.put(heading, field));
        }

        return suggestions;
    }

    /**
     * A heading flattened to letters and digits, lower case.
     *
     * The one comparison the automatic matching is built on, and the same
     * flattening is reused for reading values that name a fixed choice — so
     * "In Stock", "in-stock" and "INSTOCK" are one answer wherever they turn up.
     */
    /**
     * Whether an option axis is a colour, judged by what the file calls it.
     *
     * FluxiBiz sells an option as a size and a colour together, and only a
     * colour gets a swatch on the storefront. A file whose second option is
     * Material or Flavour is not wrong — it simply has no colour — so the name
     * of the axis decides, rather than its position.
     */
    public static boolean isColourAxis(String axisName) {
        String normalized = normalize(axisName);

        return normalized.equals("color") || normalized.equals("colour");
    }

    public static String normalize(String heading) {
        if (heading == null) {
            return "";
        }

        return heading.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
