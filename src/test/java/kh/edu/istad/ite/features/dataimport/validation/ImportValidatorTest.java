package kh.edu.istad.ite.features.dataimport.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.features.dataimport.canonical.ItemGroupImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.canonical.ItemImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.canonical.OpeningStockImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.RowOptions;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.UnitCategory;
import kh.edu.istad.ite.shared.enums.ImportIssueSeverity;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;

class ImportValidatorTest {

    private static final UUID BUSINESS = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();

    private final ItemGroupImportValidator groupValidator = new ItemGroupImportValidator();
    private final ItemImportValidator itemValidator = new ItemImportValidator();
    private final OpeningStockImportValidator stockValidator = new OpeningStockImportValidator();

    // --- fixtures ------------------------------------------------------------------

    private Unit unit(String name) {
        Unit unit = new Unit();
        unit.setId(UNIT_ID);
        unit.setName(name);
        unit.setSlug(name.toLowerCase());
        return unit;
    }

    private ItemGroup group(String name, ItemGroup parent) {
        ItemGroup group = new ItemGroup();
        group.setId(UUID.randomUUID());
        group.setName(name);
        group.setParent(parent);
        return group;
    }

    private Item item(String name, String sku, String barcode, ItemType type, boolean tracked) {
        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setName(name);
        item.setSku(sku);
        item.setBarcode(barcode);
        item.setItemType(type);
        item.setTrackInventory(tracked);
        return item;
    }

    private ValidationContext context(
            List<ItemGroup> groups,
            List<Item> items,
            Set<UUID> withStock,
            Set<UUID> withVariants
    ) {
        return context(groups, items, withStock, withVariants, List.of());
    }

    /** A shop, plus whatever units the uploaded workbook declared for itself. */
    private ValidationContext context(
            List<ItemGroup> groups,
            List<Item> items,
            Set<UUID> withStock,
            Set<UUID> withVariants,
            List<DeclaredUnit> declaredUnits
    ) {
        return new ValidationContext(
                BUSINESS, groups, List.of(unit("Piece")), items, withStock, withVariants, declaredUnits);
    }

    private ValidationContext emptyShop() {
        return context(List.of(), List.of(), Set.of(), Set.of());
    }

    private MappingPlan plan(ImportTargetType target, ImportDuplicateStrategy strategy, UUID defaultUnit) {
        Map<ImportField, String> mapped = new EnumMap<>(ImportField.class);
        mapped.put(ImportField.NAME, "name");
        mapped.put(ImportField.SKU, "sku");
        mapped.put(ImportField.OPENING_STOCK, "qty");

        return new MappingPlan(target, mapped, strategy, defaultUnit, null);
    }

    private ItemImportRecord anItem(String name, String sku, BigDecimal openingStock) {
        return new ItemImportRecord(
                name, sku, null, "Drinks", null, null, ItemType.PHYSICAL,
                BigDecimal.ONE, BigDecimal.ONE, null, null,
                null, null, ItemStatus.ACTIVE, openingStock, null, RowOptions.NONE, null);
    }

    private List<String> codesOf(RowVerdict verdict) {
        return verdict.issues().stream().map(RowIssue::code).toList();
    }

    private List<String> messagesOf(RowVerdict verdict) {
        return verdict.issues().stream().map(RowIssue::message).toList();
    }

    // --- item groups ---------------------------------------------------------------

    @Test
    void refusesACategoryWithNoName() {
        RowVerdict verdict = groupValidator.validate(
                new ItemGroupImportRecord(null, null, null), 2, emptyShop(),
                plan(ImportTargetType.ITEM_GROUP, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("MISSING_NAME");
    }

    @Test
    void marksACategoryTheShopAlreadyHasAsADuplicate() {
        ItemGroup existing = group("Drinks", null);
        RowVerdict verdict = groupValidator.validate(
                new ItemGroupImportRecord("drinks", null, null), 2,
                context(List.of(existing), List.of(), Set.of(), Set.of()),
                plan(ImportTargetType.ITEM_GROUP, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(verdict.matchedEntityId()).isEqualTo(existing.getId());
    }

    /** Categories go two deep; a third level has to be refused before commit. */
    @Test
    void refusesAParentThatIsAlreadyASubCategory() {
        ItemGroup top = group("Drinks", null);
        ItemGroup sub = group("Coffee", top);

        RowVerdict verdict = groupValidator.validate(
                new ItemGroupImportRecord("Espresso", null, "Coffee"), 2,
                context(List.of(top, sub), List.of(), Set.of(), Set.of()),
                plan(ImportTargetType.ITEM_GROUP, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("PARENT_TOO_DEEP");
    }

    /** A parent created earlier in the same file counts as existing. */
    @Test
    void acceptsAParentThisFileIsAboutToCreate() {
        ValidationContext context = emptyShop();
        MappingPlan plan = plan(ImportTargetType.ITEM_GROUP, ImportDuplicateStrategy.SKIP, null);

        groupValidator.validate(new ItemGroupImportRecord("Drinks", null, null), 2, context, plan);
        RowVerdict child = groupValidator.validate(
                new ItemGroupImportRecord("Coffee", null, "Drinks"), 3, context, plan);

        assertThat(child.status()).isEqualTo(ImportRowStatus.VALID);
    }

    // --- items ---------------------------------------------------------------------

    @Test
    void acceptsAnItemAndPromisesToCreateItsCategory() {
        RowVerdict verdict = itemValidator.validate(
                anItem("Espresso", "ESP-1", null), 2, emptyShop(),
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(codesOf(verdict)).contains("ITEM_GROUP_WILL_BE_CREATED");
    }

    @Test
    void refusesAnItemWithNoUnitAndNoDefault() {
        RowVerdict verdict = itemValidator.validate(
                anItem("Espresso", "ESP-1", null), 2, emptyShop(),
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("MISSING_UNIT");
    }

    @Test
    void marksTheSecondRowWithTheSameSkuAsADuplicate() {
        ValidationContext context = emptyShop();
        MappingPlan plan = plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID);

        itemValidator.validate(anItem("Espresso", "ESP-1", null), 2, context, plan);
        RowVerdict second = itemValidator.validate(anItem("Espresso Single", "ESP-1", null), 3, context, plan);

        assertThat(second.status()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(codesOf(second)).contains("DUPLICATE_IN_FILE");
        assertThat(second.issues().getFirst().message()).contains("Row 2");
    }

    /** A row pointing at two different existing items cannot be guessed at. */
    @Test
    void refusesARowThatMatchesTwoDifferentItems()  {
        Item bySku = item("Espresso", "ESP-1", null, ItemType.PHYSICAL, true);
        Item byName = item("Latte", "LAT-9", null, ItemType.PHYSICAL, true);

        RowVerdict verdict = itemValidator.validate(
                anItem("Latte", "ESP-1", null), 2,
                context(List.of(), List.of(bySku, byName), Set.of(), Set.of()),
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("AMBIGUOUS_MATCH");
    }

    /** A service has no shelf, so a quantity against one is a matching mistake. */
    @Test
    void refusesOpeningStockOnSomethingThatIsNotCounted() {
        ItemImportRecord service = new ItemImportRecord(
                "Repair", null, null, "Services", null, null, ItemType.SERVICE,
                BigDecimal.TEN, null, null, null,
                null, null, ItemStatus.ACTIVE, new BigDecimal("5"), null, RowOptions.NONE, null);

        RowVerdict verdict = itemValidator.validate(
                service, 2, emptyShop(),
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("STOCK_NOT_TRACKED");
    }

    @Test
    void warnsWhenOpeningStockArrivesWithNoCost() {
        ItemImportRecord noCost = new ItemImportRecord(
                "Espresso", "ESP-1", null, "Drinks", null, null, ItemType.PHYSICAL,
                BigDecimal.ONE, null, null, null,
                null, null, ItemStatus.ACTIVE, new BigDecimal("10"), null, RowOptions.NONE, null);

        RowVerdict verdict = itemValidator.validate(
                noCost, 2, emptyShop(),
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(codesOf(verdict)).contains("COST_ASSUMED_ZERO");
    }

    // --- opening stock -------------------------------------------------------------

    @Test
    void refusesAQuantityForAnItemTheShopDoesNotHave() {
        RowVerdict verdict = stockValidator.validate(
                new OpeningStockImportRecord("NOPE-1", null, null, BigDecimal.TEN, BigDecimal.ONE),
                2, emptyShop(), plan(ImportTargetType.OPENING_STOCK, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("ITEM_NOT_FOUND");
    }

    /**
     * The ledger allows one opening balance per item, so a re-import must
     * leave anything already counted alone rather than add to it.
     */
    @Test
    void skipsAnItemThatAlreadyHasStockRecorded() {
        Item counted = item("Espresso", "ESP-1", null, ItemType.PHYSICAL, true);

        RowVerdict verdict = stockValidator.validate(
                new OpeningStockImportRecord("ESP-1", null, null, BigDecimal.TEN, BigDecimal.ONE),
                2, context(List.of(), List.of(counted), Set.of(counted.getId()), Set.of()),
                plan(ImportTargetType.OPENING_STOCK, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(codesOf(verdict)).contains("STOCK_ALREADY_RECORDED");
    }

    @Test
    void refusesAQuantityForAnItemSoldInOptions() {
        Item withOptions = item("T-Shirt", "TS-1", null, ItemType.PHYSICAL, true);

        RowVerdict verdict = stockValidator.validate(
                new OpeningStockImportRecord("TS-1", null, null, BigDecimal.TEN, BigDecimal.ONE),
                2, context(List.of(), List.of(withOptions), Set.of(), Set.of(withOptions.getId())),
                plan(ImportTargetType.OPENING_STOCK, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("SOLD_IN_OPTIONS");
    }

    @Test
    void refusesARowThatDoesNotSayWhichItemItIsFor() {
        RowVerdict verdict = stockValidator.validate(
                new OpeningStockImportRecord(null, null, null, BigDecimal.TEN, null),
                2, emptyShop(), plan(ImportTargetType.OPENING_STOCK, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("MISSING_IDENTIFIER");
    }

    @Test
    void refusesANegativeQuantity() {
        Item counted = item("Espresso", "ESP-1", null, ItemType.PHYSICAL, true);

        RowVerdict verdict = stockValidator.validate(
                new OpeningStockImportRecord("ESP-1", null, null, new BigDecimal("-5"), BigDecimal.ONE),
                2, context(List.of(), List.of(counted), Set.of(), Set.of()),
                plan(ImportTargetType.OPENING_STOCK, ImportDuplicateStrategy.SKIP, null));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("NEGATIVE_QUANTITY");
    }

    // --- items sold in options -----------------------------------------------------

    private ItemImportRecord anOption(
            String name, String groupKey, String sku, String size, String colour) {
        return new ItemImportRecord(
                name, sku, null, "Apparel", null, null, ItemType.PHYSICAL,
                new BigDecimal("19.99"), new BigDecimal("5.50"), null, null,
                null, null, ItemStatus.ACTIVE, new BigDecimal("10"), groupKey,
                RowOptions.of("Size", size, "Color", colour), null);
    }

    /**
     * A variant export repeats the item's name on every row on purpose. Without
     * grouping, the second row of every item would be called a duplicate of the
     * first and nothing with options could ever be imported.
     */
    @Test
    void treatsRowsSharingAGroupAsOneItemRatherThanDuplicates() {
        ValidationContext context = emptyShop();
        MappingPlan plan = plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID);

        RowVerdict first = itemValidator.validate(
                anOption("T-Shirt", "APP-TS-001", "TS-S-BLK", "Small", "Black"), 2, context, plan);
        RowVerdict second = itemValidator.validate(
                anOption("T-Shirt", "APP-TS-001", "TS-M-BLK", "Medium", "Black"), 3, context, plan);

        assertThat(first.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(second.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(context.distinctGroups()).isEqualTo(1);
    }

    /** The same size in the same colour twice is one shelf counted twice over. */
    @Test
    void refusesTheSameOptionTwiceWithinAnItem() {
        ValidationContext context = emptyShop();
        MappingPlan plan = plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID);

        itemValidator.validate(
                anOption("T-Shirt", "APP-TS-001", "TS-S-BLK", "Small", "Black"), 2, context, plan);
        RowVerdict repeat = itemValidator.validate(
                anOption("T-Shirt", "APP-TS-001", "TS-S-BLK-2", "Small", "Black"), 3, context, plan);

        assertThat(repeat.status()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(codesOf(repeat)).contains("DUPLICATE_OPTION_IN_FILE");
    }

    /** Each option is scanned and counted on its own, so its code must be its own. */
    @Test
    void stillRefusesTwoOptionsSharingASku() {
        ValidationContext context = emptyShop();
        MappingPlan plan = plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID);

        itemValidator.validate(
                anOption("T-Shirt", "APP-TS-001", "TS-DUP", "Small", "Black"), 2, context, plan);
        RowVerdict clash = itemValidator.validate(
                anOption("T-Shirt", "APP-TS-001", "TS-DUP", "Medium", "Black"), 3, context, plan);

        assertThat(clash.status()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(codesOf(clash)).contains("DUPLICATE_IN_FILE");
    }

    /** Two shirts are two items even when their sizes are named the same. */
    @Test
    void keepsSeparateGroupsApart() {
        ValidationContext context = emptyShop();
        MappingPlan plan = plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID);

        itemValidator.validate(
                anOption("T-Shirt", "APP-TS-001", "TS-S", "Small", "Black"), 2, context, plan);
        RowVerdict other = itemValidator.validate(
                anOption("Hoodie", "APP-HD-001", "HD-S", "Small", "Black"), 3, context, plan);

        assertThat(other.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(context.distinctGroups()).isEqualTo(2);
    }

    /**
     * An item is filed on a leaf, never on a parent. The catalogue refuses it,
     * so the row has to be refused at checking — otherwise a shop agrees to an
     * import that then fails item by item for a reason it never mentioned.
     */
    @Test
    void refusesAnItemFiledOnACategoryThatHasSubCategories() {
        ItemGroup beverages = group("Beverages", null);
        ItemGroup coffee = group("Coffee", beverages);

        ItemImportRecord onParent = new ItemImportRecord(
                "Latte", "LAT-1", null, "Beverages", null, null, ItemType.PHYSICAL,
                BigDecimal.ONE, BigDecimal.ONE, null, null,
                null, null, ItemStatus.ACTIVE, null, null, RowOptions.NONE, null);

        RowVerdict verdict = itemValidator.validate(
                onParent, 2, context(List.of(beverages, coffee), List.of(), Set.of(), Set.of()),
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("CATEGORY_HAS_SUBCATEGORIES");
    }

    /** A leaf category is exactly where an item belongs. */
    @Test
    void acceptsAnItemFiledOnALeafCategory() {
        ItemGroup beverages = group("Beverages", null);
        ItemGroup coffee = group("Coffee", beverages);

        ItemImportRecord onLeaf = new ItemImportRecord(
                "Latte", "LAT-1", null, "Coffee", null, null, ItemType.PHYSICAL,
                BigDecimal.ONE, BigDecimal.ONE, null, null,
                null, null, ItemStatus.ACTIVE, null, null, RowOptions.NONE, null);

        RowVerdict verdict = itemValidator.validate(
                onLeaf, 2, context(List.of(beverages, coffee), List.of(), Set.of(), Set.of()),
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.VALID);
    }

    private ItemImportRecord filedUnder(String name, String category, String parent) {
        return new ItemImportRecord(
                name, name + "-1", null, category, parent, null, ItemType.PHYSICAL,
                BigDecimal.ONE, BigDecimal.ONE, null, null,
                null, null, ItemStatus.ACTIVE, null, null, RowOptions.NONE, null);
    }

    private RowVerdict judge(ItemImportRecord record, ValidationContext context) {
        return itemValidator.validate(
                record, 2, context,
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID));
    }

    /**
     * The refusal has to be actionable. "Beverages has sub-categories" sends the
     * shop off to look them up before they can fix a single row; naming them
     * lets the whole file be corrected in one pass.
     */
    @Test
    void namesTheSubCategoriesAnItemCouldHaveGoneIn() {
        ItemGroup beverages = group("Beverages", null);
        ValidationContext context = context(
                List.of(beverages, group("Coffee", beverages), group("Tea", beverages)),
                List.of(), Set.of(), Set.of());

        RowVerdict verdict = judge(filedUnder("Latte", "Beverages", null), context);

        assertThat(messagesOf(verdict))
                .anySatisfy(message -> assertThat(message).contains("Coffee").contains("Tea"));
    }

    /**
     * The point of the parent column: a flat file could only ever name a
     * top-level category, which is the one place an item cannot live once the
     * shop has sub-categories.
     */
    @Test
    void createsACategoryUnderTheParentTheFileNames() {
        RowVerdict verdict = judge(filedUnder("Latte", "Coffee", "Beverages"), emptyShop());

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(messagesOf(verdict)).anySatisfy(message ->
                assertThat(message).contains("\"Coffee\" will be created under \"Beverages\""));
    }

    /**
     * A file putting one item in a sub-category has made its parent a parent, so
     * a later row filing an item straight onto it has to be refused for the same
     * reason an existing parent would refuse it.
     */
    @Test
    void refusesAnItemOnACategoryThisFileIsAboutToGiveChildren() {
        ValidationContext context = emptyShop();

        judge(filedUnder("Latte", "Coffee", "Beverages"), context);
        RowVerdict later = judge(filedUnder("Water", "Beverages", null), context);

        assertThat(later.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(later)).contains("CATEGORY_HAS_SUBCATEGORIES");
    }

    /** Categories go two deep, so a sub-category can never itself be a parent. */
    @Test
    void refusesAParentThatIsAlreadySomebodysSubCategory() {
        ItemGroup beverages = group("Beverages", null);
        ValidationContext context = context(
                List.of(beverages, group("Coffee", beverages)), List.of(), Set.of(), Set.of());

        RowVerdict verdict = judge(filedUnder("Latte", "Espresso Drinks", "Coffee"), context);

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("PARENT_IS_A_SUBCATEGORY");
    }

    /**
     * Moving a category takes every item already in it along, which is far more
     * than importing a price list should do. The row still imports.
     */
    @Test
    void leavesACategoryWhereItIsWhenTheFileDisagrees() {
        ItemGroup beverages = group("Beverages", null);
        ValidationContext context = context(
                List.of(beverages, group("Coffee", beverages), group("Food", null)),
                List.of(), Set.of(), Set.of());

        RowVerdict verdict = judge(filedUnder("Latte", "Coffee", "Food"), context);

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(codesOf(verdict)).contains("CATEGORY_ALREADY_HAS_A_PARENT");
    }

    @Test
    void refusesACategoryFiledUnderItself() {
        RowVerdict verdict = judge(filedUnder("Latte", "Coffee", "coffee"), emptyShop());

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("CATEGORY_IS_ITS_OWN_PARENT");
    }

    /**
     * An existing category keeps the parent it has, so promising to create the
     * one the file names would put a category in the report that never appears
     * in the shop.
     */
    @Test
    void doesNotPromiseAParentForACategoryThatAlreadyExists() {
        ValidationContext context = context(
                List.of(group("Coffee", null)), List.of(), Set.of(), Set.of());

        RowVerdict verdict = judge(filedUnder("Latte", "Coffee", "Beverages"), context);

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(codesOf(verdict)).doesNotContain("PARENT_GROUP_WILL_BE_CREATED");
    }

    private ItemImportRecord countedIn(String unitName) {
        return new ItemImportRecord(
                "Rice", "RICE-1", null, "Groceries", null, unitName, ItemType.PHYSICAL,
                BigDecimal.ONE, BigDecimal.ONE, null, null,
                null, null, ItemStatus.ACTIVE, null, null, RowOptions.NONE, null);
    }

    private static final DeclaredUnit KILOGRAM =
            new DeclaredUnit("Kilogram", "kg", UnitCategory.MASS, null);

    /** A unit the shop already has is used as it stands, and says nothing. */
    @Test
    void reusesAUnitTheShopAlreadyHas() {
        RowVerdict verdict = judge(countedIn("Piece"), emptyShop());

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(codesOf(verdict)).doesNotContain("UNIT_NOT_FOUND", "UNIT_WILL_BE_CREATED");
    }

    /** Written as its symbol, or its name, or its slug — all the same unit. */
    @Test
    void findsAUnitByWhateverTheRowCallsIt() {
        assertThat(judge(countedIn("piece"), emptyShop()).status())
                .isEqualTo(ImportRowStatus.VALID);
    }

    /**
     * The point of the Units sheet: a shop counting in sacks should not have to
     * go and create the unit somewhere else and start the import again.
     */
    @Test
    void createsAUnitTheWorkbookDeclares() {
        ValidationContext context =
                context(List.of(), List.of(), Set.of(), Set.of(), List.of(KILOGRAM));

        RowVerdict verdict = judge(countedIn("kg"), context);

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(codesOf(verdict)).contains("UNIT_WILL_BE_CREATED");
        assertThat(messagesOf(verdict))
                .anySatisfy(message -> assertThat(message).contains("Kilogram (kg)"));
    }

    /**
     * "Will be created" is the import saying what it is about to do, not a
     * problem — the row has to stay importable and the note has to read as
     * information rather than as something to fix.
     */
    @Test
    void treatsAUnitItWillCreateAsInformationRatherThanAProblem() {
        ValidationContext context =
                context(List.of(), List.of(), Set.of(), Set.of(), List.of(KILOGRAM));

        RowVerdict verdict = judge(countedIn("kg"), context);

        assertThat(verdict.issues())
                .filteredOn(issue -> issue.code().equals("UNIT_WILL_BE_CREATED"))
                .allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(ImportIssueSeverity.INFO));
    }

    /**
     * A unit nobody has and nobody described. "sack" could be a weight or a
     * count, and being wrong about that corrupts every quantity it touches.
     */
    @Test
    void refusesAUnitNothingDefines() {
        RowVerdict verdict = judge(countedIn("sack"), emptyShop());

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("UNIT_NOT_FOUND");
    }

    /** Told the same unit measures something else, we stop rather than choose. */
    @Test
    void refusesAUnitTheFileRedefines() {
        ValidationContext context = context(
                List.of(), List.of(), Set.of(), Set.of(),
                List.of(new DeclaredUnit("Piece", "pc", UnitCategory.MASS, null)));

        RowVerdict verdict = judge(countedIn("Piece"), context);

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("UNIT_TYPE_CONFLICT");
    }

    /**
     * An item's options share its unit, because the catalogue keeps the unit on
     * the item. Whichever row was read last would otherwise decide it silently.
     */
    @Test
    void refusesOptionRowsThatDisagreeAboutTheUnit() {
        // Both units resolve, so the only thing left to complain about is that
        // the rows disagree — which is the thing under test.
        ValidationContext context =
                context(List.of(), List.of(), Set.of(), Set.of(), List.of(KILOGRAM));

        RowVerdict first = judge(optionCountedIn("Piece", "Small"), context);
        RowVerdict second = judge(optionCountedIn("kg", "Large"), context);

        assertThat(first.status()).isEqualTo(ImportRowStatus.VALID);
        assertThat(second.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(second)).contains("UNIT_CONFLICT_IN_GROUP");
    }

    /** Options agreeing about the unit are the ordinary case and pass. */
    @Test
    void acceptsOptionRowsThatShareAUnit() {
        ValidationContext context = emptyShop();

        judge(optionCountedIn("Piece", "Small"), context);
        RowVerdict second = judge(optionCountedIn("Piece", "Large"), context);

        assertThat(codesOf(second)).doesNotContain("UNIT_CONFLICT_IN_GROUP");
    }

    private ItemImportRecord optionCountedIn(String unitName, String size) {
        return new ItemImportRecord(
                "T-Shirt", "TS-" + size, null, "Apparel", null, unitName, ItemType.PHYSICAL,
                BigDecimal.ONE, BigDecimal.ONE, null, null,
                null, null, ItemStatus.ACTIVE, null, "TSHIRT-01",
                RowOptions.of("Size", size, null, null), null);
    }
}
