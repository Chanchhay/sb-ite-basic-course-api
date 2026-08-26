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
import kh.edu.istad.ite.features.dataimport.canonical.ItemImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.canonical.OpeningStockImportRecord;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
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
        return new ValidationContext(BUSINESS, groups, List.of(unit("Piece")), items, withStock, withVariants);
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
                name, sku, null, "Drinks", null, ItemType.PHYSICAL,
                BigDecimal.ONE, null, BigDecimal.ONE, null, null,
                null, null, ItemStatus.ACTIVE, openingStock);
    }

    private List<String> codesOf(RowVerdict verdict) {
        return verdict.issues().stream().map(RowIssue::code).toList();
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
                "Repair", null, null, "Services", null, ItemType.SERVICE,
                BigDecimal.TEN, null, null, null, null,
                null, null, ItemStatus.ACTIVE, new BigDecimal("5"));

        RowVerdict verdict = itemValidator.validate(
                service, 2, emptyShop(),
                plan(ImportTargetType.ITEM, ImportDuplicateStrategy.SKIP, UNIT_ID));

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(codesOf(verdict)).contains("STOCK_NOT_TRACKED");
    }

    @Test
    void warnsWhenOpeningStockArrivesWithNoCost() {
        ItemImportRecord noCost = new ItemImportRecord(
                "Espresso", "ESP-1", null, "Drinks", null, ItemType.PHYSICAL,
                BigDecimal.ONE, null, null, null, null,
                null, null, ItemStatus.ACTIVE, new BigDecimal("10"));

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
}
