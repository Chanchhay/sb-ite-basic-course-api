package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.parser.XlsxSourceFileParser;
import kh.edu.istad.ite.features.migration.transform.PreparedWorkbookWriter;
import kh.edu.istad.ite.features.migration.normalize.DataNormalizationService;
import kh.edu.istad.ite.features.migration.resolve.FieldRule;
import kh.edu.istad.ite.features.migration.resolve.MissingFieldResolutionService;
import kh.edu.istad.ite.features.migration.transform.PreparedRow;
import kh.edu.istad.ite.features.migration.transform.SourceTransformer;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.UnitCategory;

/** Confirms what the test plan claims about rule scope and precedence. */
class MissingInformationPlanTest {

    private final SourceTransformer transformer =
            new SourceTransformer(new DataNormalizationService());
    private final MissingFieldResolutionService resolver = new MissingFieldResolutionService();

    private static final Map<String, ImportField> MAPPING = Map.of(
            "name", ImportField.NAME,
            "cat", ImportField.ITEM_GROUP,
            "uom", ImportField.UNIT,
            "kind", ImportField.ITEM_TYPE);

    private SourceRow row(int number, Map<String, String> cells) {
        return new SourceRow(number, new LinkedHashMap<>(cells));
    }

    private List<PreparedRow> apply(List<SourceRow> rows, List<FieldRule> rules) {
        TransformResult result =
                transformer.transform(rows, MAPPING, ImportTargetType.ITEM, Map.of());

        resolver.resolve(result.rows(), ImportTargetType.ITEM, rules);

        return result.rows();
    }

    private FieldRule all(ImportField field, String value) {
        return new FieldRule(field, FieldRule.Scope.ALL, null, value, "all");
    }

    private FieldRule inCategory(ImportField field, String category, String value) {
        return new FieldRule(field, FieldRule.Scope.CATEGORY, category, value, "category");
    }

    private FieldRule ofType(ImportField field, String type, String value) {
        return new FieldRule(field, FieldRule.Scope.ITEM_TYPE, type, value, "item type");
    }

    private List<SourceRow> mixed() {
        return List.of(
                row(2, Map.of("name", "Shirt", "cat", "Clothing")),
                row(3, Map.of("name", "Rice", "cat", "Food")));
    }

    /** §7 — a category rule must win over the global fallback, whatever the order. */
    @Test
    void categoryRuleBeatsTheGlobalFallbackWhicheverOrderTheyArriveIn() {
        List<PreparedRow> globalFirst =
                apply(mixed(), List.of(all(ImportField.UNIT, "pc"), inCategory(ImportField.UNIT, "Food", "kg")));

        List<PreparedRow> specificFirst =
                apply(mixed(), List.of(inCategory(ImportField.UNIT, "Food", "kg"), all(ImportField.UNIT, "pc")));

        assertThat(globalFirst.get(1).get(ImportField.UNIT)).isEqualTo("kg");
        assertThat(globalFirst.getFirst().get(ImportField.UNIT)).isEqualTo("pc");
        assertThat(specificFirst.get(1).get(ImportField.UNIT)).isEqualTo("kg");
    }

    /** §4 and §3 — a rule scoped to an item type still needs that type to be known. */
    @Test
    void appliesAnItemTypeScopedRuleEvenWhenTheItemTypeItselfWasDecidedTooo() {
        List<PreparedRow> rows = apply(
                List.of(row(2, Map.of("name", "Haircut", "cat", "Salon"))),
                List.of(
                        ofType(ImportField.UNIT, "Service", "svc"),
                        all(ImportField.ITEM_TYPE, "Service")));

        assertThat(rows.getFirst().get(ImportField.ITEM_TYPE)).isEqualTo("Service");
        assertThat(rows.getFirst().get(ImportField.UNIT)).isEqualTo("svc");
    }

    /** §8 — three overlapping rules must land somewhere deterministic. */
    @Test
    void settlesThreeOverlappingRulesTheSameWayEveryTime() {
        List<FieldRule> rules = List.of(
                all(ImportField.UNIT, "pc"),
                ofType(ImportField.UNIT, "Physical", "pc"),
                inCategory(ImportField.UNIT, "Food", "kg"));

        List<PreparedRow> rows = apply(mixed(), rules);

        assertThat(rows.get(1).get(ImportField.UNIT)).isEqualTo("kg");
        assertThat(rows.getFirst().get(ImportField.UNIT)).isEqualTo("pc");
    }

    /** §3 — a rule for one item type must leave the others alone. */
    @Test
    void keepsAnItemTypeRuleToItsOwnItemType() {
        List<PreparedRow> rows = apply(
                List.of(
                        row(2, Map.of("name", "Shirt", "cat", "Clothing", "kind", "physical")),
                        row(3, Map.of("name", "Haircut", "cat", "Salon", "kind", "service")),
                        row(4, Map.of("name", "Ebook", "cat", "Books", "kind", "digital"))),
                List.of(
                        ofType(ImportField.UNIT, "Physical", "pc"),
                        ofType(ImportField.UNIT, "Service", "svc"),
                        ofType(ImportField.UNIT, "Digital", "license")));

        assertThat(rows.getFirst().get(ImportField.UNIT)).isEqualTo("pc");
        assertThat(rows.get(1).get(ImportField.UNIT)).isEqualTo("svc");
        assertThat(rows.get(2).get(ImportField.UNIT)).isEqualTo("license");
    }

    /** §6 — two category rules for the same field must not bleed into each other. */
    @Test
    void keepsTwoCategoryRulesApart() {
        List<PreparedRow> rows = apply(
                List.of(
                        row(2, Map.of("name", "Shirt", "cat", "Clothing", "kind", "physical")),
                        row(3, Map.of("name", "Cake", "cat", "Made-to-order", "kind", "physical"))),
                List.of(
                        inCategory(ImportField.TRACK_INVENTORY, "Clothing", "Yes"),
                        inCategory(ImportField.TRACK_INVENTORY, "Made-to-order", "No")));

        assertThat(rows.getFirst().get(ImportField.TRACK_INVENTORY)).isEqualTo("Yes");
        assertThat(rows.get(1).get(ImportField.TRACK_INVENTORY)).isEqualTo("No");
    }

    /** §16 — a real column beats a fallback rule set before it was mapped. */
    @Test
    void letsRealSourceDataOverrideAnEarlierFallback() {
        List<PreparedRow> rows = apply(
                List.of(row(2, Map.of("name", "Rice", "cat", "Food", "uom", "kg"))),
                List.of(all(ImportField.UNIT, "pc")));

        assertThat(rows.getFirst().get(ImportField.UNIT)).isEqualTo("kg");
    }

    /** §13 — resolving part of a group leaves the rest counted and still blocking. */
    @Test
    void countsWhatIsLeftAfterAPartialDecision() {
        TransformResult result = transformer.transform(
                List.of(
                        row(2, Map.of("name", "Shirt", "cat", "Clothing")),
                        row(3, Map.of("name", "Rice", "cat", "Food")),
                        row(4, Map.of("name", "Bread", "cat", "Food"))),
                MAPPING, ImportTargetType.ITEM, Map.of());

        MissingFieldResolutionService.Outcome outcome = resolver.resolve(
                result.rows(),
                ImportTargetType.ITEM,
                List.of(inCategory(ImportField.UNIT, "Food", "kg")));

        assertThat(outcome.report().fields())
                .filteredOn(status -> status.field() == ImportField.UNIT)
                .singleElement()
                .satisfies(status -> {
                    assertThat(status.filled()).isEqualTo(2);
                    assertThat(status.missing()).isEqualTo(1);
                    assertThat(status.blocking()).isTrue();
                });

        assertThat(outcome.findings())
                .filteredOn(f -> ImportField.UNIT.name().equals(f.targetField()))
                .hasSize(1);
    }

    /** The plan's mixed catalogue, migrated with six grouped decisions and no row editing. */
    @Test
    void migratesAMixedCatalogueWithSixDecisions() {
        List<SourceRow> catalogue = List.of(
                row(2, Map.of("name", "Shirt", "cat", "Clothing")),
                row(3, Map.of("name", "Sneakers", "cat", "Shoes")),
                row(4, Map.of("name", "Rice", "cat", "Food")),
                row(5, Map.of("name", "Cola", "cat", "Drinks")),
                row(6, Map.of("name", "Haircut", "cat", "Services")),
                row(7, Map.of("name", "Ebook", "cat", "Digital")));

        List<FieldRule> rules = List.of(
                inCategory(ImportField.ITEM_TYPE, "Services", "Service"),
                inCategory(ImportField.ITEM_TYPE, "Digital", "Digital"),
                all(ImportField.ITEM_TYPE, "Physical"),
                inCategory(ImportField.UNIT, "Clothing", "pc"),
                inCategory(ImportField.UNIT, "Shoes", "pair"),
                inCategory(ImportField.UNIT, "Food", "kg"),
                inCategory(ImportField.UNIT, "Drinks", "L"),
                inCategory(ImportField.UNIT, "Services", "svc"),
                inCategory(ImportField.UNIT, "Digital", "license"),
                all(ImportField.TRACK_INVENTORY, "Yes"));

        TransformResult result =
                transformer.transform(catalogue, MAPPING, ImportTargetType.ITEM, Map.of());
        MissingFieldResolutionService.Outcome outcome =
                resolver.resolve(result.rows(), ImportTargetType.ITEM, rules);

        List<PreparedRow> rows = result.rows();

        assertThat(rows.stream().map(r -> r.get(ImportField.UNIT)).toList())
                .containsExactly("pc", "pair", "kg", "L", "svc", "license");

        assertThat(rows.get(4).get(ImportField.TRACK_INVENTORY)).isEqualTo("No");
        assertThat(rows.get(5).get(ImportField.TRACK_INVENTORY)).isEqualTo("No");
        assertThat(rows.getFirst().get(ImportField.TRACK_INVENTORY)).isEqualTo("Yes");

        assertThat(outcome.findings()).isEmpty();
    }

    /**
     * §20 — the decision, the summary and the workbook must be the same thing.
     *
     * The one check that catches a whole class of quiet failure. Everything
     * upstream can agree that a migration is resolved while the file handed
     * over says something else, and the shop is the one who finds out. So the
     * workbook is read back through the importer's own reader, and both halves
     * of a unit decision are checked: the symbol on the row, and the
     * declaration on the Units sheet that tells the importer what it means.
     */
    @Test
    void writesEveryDecisionIntoTheFileTheImporterActuallyReceives() throws Exception {
        TransformResult result = transformer.transform(
                List.of(row(2, Map.of("name", "Sneakers", "cat", "Shoes"))),
                MAPPING, ImportTargetType.ITEM, Map.of());

        resolver.resolve(result.rows(), ImportTargetType.ITEM, List.of(
                all(ImportField.ITEM_TYPE, "Physical"),
                all(ImportField.TRACK_INVENTORY, "Yes"),
                inCategory(ImportField.UNIT, "Shoes", "pair")));

        /*
         * The unit an operator invented has to be declared as well as used.
         * A symbol on a row with nothing on the Units sheet is exactly the
         * shape of failure this test exists to catch.
         */
        List<DeclaredUnit> units = List.of(
                new DeclaredUnit("Pair", "pair", UnitCategory.COUNT, "Chosen during migration"));

        PreparedWorkbookWriter writer = new PreparedWorkbookWriter();
        byte[] workbook = writer.write(
                writer.shapeFor(ImportTargetType.ITEM, result.rows()), result.rows(), units);

        XlsxSourceFileParser parser = new XlsxSourceFileParser();
        List<SourceRow> readBack = new ArrayList<>();

        parser.readRows(new ByteArrayInputStream(workbook), 100, readBack::add);

        assertThat(readBack).singleElement().satisfies(written -> {
            assertThat(written.value("Name")).isEqualTo("Sneakers");
            assertThat(written.value("Unit")).isEqualTo("pair");
            assertThat(written.value("Item Type")).isEqualTo("Physical");
            assertThat(written.value("Track Stock")).isEqualTo("Yes");
        });

        List<SourceRow> declared = parser.readNamedSheet(
                new ByteArrayInputStream(workbook), XlsxSourceFileParser.UNITS_SHEET, 100);

        assertThat(declared).singleElement().satisfies(unit -> {
            assertThat(unit.value("Name")).isEqualTo("Pair");
            assertThat(unit.value("Short Symbol")).isEqualTo("pair");
            assertThat(unit.value("Type")).isEqualTo("COUNT");
        });

        SourceFileParser.SourceHeader header =
                parser.readHeader(new ByteArrayInputStream(workbook), 10);

        for (String column : header.columns()) {
            assertThat(ImportField.suggestFor(column, ImportTargetType.ITEM))
                    .as("heading %s", column)
                    .isPresent();
        }
    }
}
