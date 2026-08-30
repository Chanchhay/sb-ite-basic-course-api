package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.migration.normalize.DataNormalizationService;
import kh.edu.istad.ite.features.migration.resolve.FieldRule;
import kh.edu.istad.ite.features.migration.resolve.MissingFieldReport;
import kh.edu.istad.ite.features.migration.resolve.MissingFieldResolutionService;
import kh.edu.istad.ite.features.migration.transform.PreparedRow;
import kh.edu.istad.ite.features.migration.transform.SourceTransformer;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.shared.enums.FieldResolutionSource;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

/**
 * What FluxiBiz may settle for a shop, and what it must ask about.
 *
 * The line these tests hold is the whole argument of the feature. A rule the
 * catalogue enforces anyway can be applied without a word — nothing was
 * assumed, only restated. Anything that is merely usually true has to be put
 * to somebody, because a value invented to make an import succeed arrives
 * looking exactly like a value the customer gave us, and is discovered months
 * later as a shelf of items counted in the wrong unit.
 */
class MissingFieldResolutionTest {

    private final SourceTransformer transformer =
            new SourceTransformer(new DataNormalizationService());
    private final MissingFieldResolutionService resolver = new MissingFieldResolutionService();

    private static final Map<String, ImportField> MAPPING = Map.of(
            "name", ImportField.NAME,
            "code", ImportField.SKU,
            "cat", ImportField.ITEM_GROUP,
            "uom", ImportField.UNIT,
            "kind", ImportField.ITEM_TYPE,
            "track", ImportField.TRACK_INVENTORY,
            "barcode", ImportField.BARCODE);

    private SourceRow row(int number, Map<String, String> cells) {
        return new SourceRow(number, new LinkedHashMap<>(cells));
    }

    private MissingFieldResolutionService.Outcome resolve(List<SourceRow> rows) {
        return resolve(rows, List.of());
    }

    private MissingFieldResolutionService.Outcome resolve(
            List<SourceRow> rows,
            List<FieldRule> rules
    ) {
        TransformResult result =
                transformer.transform(rows, MAPPING, ImportTargetType.ITEM, Map.of());

        return resolver.resolve(result.rows(), ImportTargetType.ITEM, rules);
    }

    private List<PreparedRow> rowsAfter(List<SourceRow> rows, List<FieldRule> rules) {
        TransformResult result =
                transformer.transform(rows, MAPPING, ImportTargetType.ITEM, Map.of());

        resolver.resolve(result.rows(), ImportTargetType.ITEM, rules);

        return result.rows();
    }

    private MissingFieldReport.FieldStatus statusOf(
            MissingFieldResolutionService.Outcome outcome,
            ImportField field
    ) {
        return outcome.report().fields().stream()
                .filter(status -> status.field() == field)
                .findFirst()
                .orElseThrow();
    }

    /**
     * A service has no shelf, so this is a restatement rather than a decision.
     *
     * FluxiBiz refuses stock against a service whatever anyone chooses, which
     * is exactly what makes deriving it safe: nothing was assumed about the
     * shop's business, only about what the catalogue will do.
     */
    @Test
    void decidesThatAServiceDoesNotHoldStockWithoutAsking() {
        List<PreparedRow> rows = rowsAfter(List.of(
                row(2, Map.of("name", "Haircut", "cat", "Salon", "uom", "pc", "kind", "service"))),
                List.of());

        PreparedRow only = rows.getFirst();

        assertThat(only.get(ImportField.TRACK_INVENTORY)).isEqualTo("No");
        assertThat(only.originOf(ImportField.TRACK_INVENTORY).resolution())
                .isEqualTo(FieldResolutionSource.DERIVED);
    }

    /** A download never runs out either, and for the same reason. */
    @Test
    void decidesThatADigitalItemDoesNotHoldStockWithoutAsking() {
        List<PreparedRow> rows = rowsAfter(List.of(
                row(2, Map.of("name", "Ebook", "cat", "Books", "uom", "pc", "kind", "digital"))),
                List.of());

        assertThat(rows.getFirst().get(ImportField.TRACK_INVENTORY)).isEqualTo("No");
    }

    /**
     * A physical item is the case FluxiBiz genuinely does not know.
     *
     * Most shops count their stock and some deliberately do not, and the
     * difference is a fact about the business rather than about the catalogue.
     * Guessing would quietly turn a shop's entire untracked range into tracked
     * items, or the reverse, with nothing on screen to say so.
     */
    @Test
    void asksWhetherPhysicalItemsCountTheirStock() {
        MissingFieldResolutionService.Outcome outcome = resolve(List.of(
                row(2, Map.of("name", "Rice", "cat", "Dry", "uom", "kg", "kind", "physical")),
                row(3, Map.of("name", "Beans", "cat", "Dry", "uom", "kg", "kind", "physical"))));

        assertThat(statusOf(outcome, ImportField.TRACK_INVENTORY).missing()).isEqualTo(2);
        assertThat(outcome.findings())
                .filteredOn(f -> f.targetField().equals(ImportField.TRACK_INVENTORY.name()))
                .hasSize(2)
                .allMatch(TransformResult.Finding::blocking);
    }

    /**
     * One answer for three thousand rows.
     *
     * The point of grouping a question is that answering it once is enough.
     * An operator asked the same thing per row answers the first few carefully
     * and the rest by reflex, which is worse than not asking.
     */
    @Test
    void appliesOneDecisionToEveryRowThatRaisedTheQuestion() {
        List<FieldRule> rules = List.of(new FieldRule(
                ImportField.TRACK_INVENTORY,
                FieldRule.Scope.ALL,
                null,
                "Yes",
                "Operator set Track Stock to \"Yes\" for every row without one"));

        List<PreparedRow> rows = rowsAfter(List.of(
                row(2, Map.of("name", "Rice", "cat", "Dry", "uom", "kg", "kind", "physical")),
                row(3, Map.of("name", "Beans", "cat", "Dry", "uom", "kg", "kind", "physical"))),
                rules);

        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get(ImportField.TRACK_INVENTORY)).isEqualTo("Yes");
            assertThat(row.originOf(ImportField.TRACK_INVENTORY).resolution())
                    .isEqualTo(FieldResolutionSource.OPERATOR_RESOLUTION);
        });
    }

    /**
     * A narrower decision beats a broader one.
     *
     * Otherwise "services are counted in services, everything else in pieces"
     * would have to be expressed as one rule and a list of exceptions, which
     * is the point at which an operator stops trusting the screen.
     */
    @Test
    void letsADecisionAboutOneCategoryOverrideTheDecisionAboutTheRest() {
        List<FieldRule> rules = List.of(
                new FieldRule(ImportField.UNIT, FieldRule.Scope.CATEGORY, "Salon", "svc", "Salon rule"),
                new FieldRule(ImportField.UNIT, FieldRule.Scope.ALL, null, "pc", "Everything else"));

        List<PreparedRow> rows = rowsAfter(List.of(
                row(2, Map.of("name", "Haircut", "cat", "Salon")),
                row(3, Map.of("name", "Shampoo", "cat", "Retail"))),
                rules);

        assertThat(rows.getFirst().get(ImportField.UNIT)).isEqualTo("svc");
        assertThat(rows.get(1).get(ImportField.UNIT)).isEqualTo("pc");
    }

    /**
     * A unit cannot be read off a product's name.
     *
     * "Coca Cola" is sold by the can, the bottle, the crate and the litre
     * depending on the shop, and a unit read wrongly does not announce itself
     * — it silently changes what every quantity counted in it means.
     */
    @Test
    void refusesToInventAUnitAndBlocksInstead() {
        MissingFieldResolutionService.Outcome outcome = resolve(List.of(
                row(2, Map.of("name", "Coca Cola", "cat", "Drinks", "kind", "physical"))));

        assertThat(statusOf(outcome, ImportField.UNIT).missing()).isEqualTo(1);
        assertThat(statusOf(outcome, ImportField.UNIT).blocking()).isTrue();
        assertThat(outcome.findings())
                .anyMatch(f -> f.targetField().equals(ImportField.UNIT.name()) && f.blocking());
    }

    /**
     * A name is the one thing no decision can supply.
     *
     * Every other gap has an answer somebody is entitled to give. Nobody is
     * entitled to name another shop's products, so these rows stay blocked
     * however many rules are set.
     */
    @Test
    void leavesRowsWithNoNameBlockedWhateverElseIsDecided() {
        MissingFieldResolutionService.Outcome outcome = resolve(
                List.of(row(2, Map.of("code", "P001", "cat", "Dry", "uom", "kg", "kind", "physical"))),
                List.of(new FieldRule(
                        ImportField.NAME, FieldRule.Scope.ALL, null, "Unknown item", "A bad idea")));

        assertThat(outcome.findings()).anyMatch(f -> f.code().equals("NAME_MISSING") && f.blocking());
    }

    /**
     * An absent barcode is an answer, not a problem.
     *
     * Reporting every optional gap would be true and useless — four thousand
     * items without a parent category would bury the one field nobody has
     * chosen a value for.
     */
    @Test
    void saysNothingAboutTheOptionalFieldsNobodyFilledIn() {
        MissingFieldResolutionService.Outcome outcome = resolve(List.of(
                row(2, Map.of("name", "Rice", "cat", "Dry", "uom", "kg", "kind", "physical",
                        "track", "yes"))));

        assertThat(outcome.report().fields())
                .noneMatch(status -> status.field() == ImportField.BARCODE);
        assertThat(outcome.findings()).isEmpty();
    }

    /**
     * An item type settled by a rule settles the stock question behind it.
     *
     * Otherwise an operator who says "these are all services" is asked
     * immediately afterwards whether services hold stock, which FluxiBiz
     * already knows the answer to.
     */
    @Test
    void followsOneDecisionThroughToTheRuleItUnlocks() {
        List<PreparedRow> rows = rowsAfter(
                List.of(row(2, Map.of("name", "Haircut", "cat", "Salon", "uom", "pc"))),
                List.of(new FieldRule(
                        ImportField.ITEM_TYPE, FieldRule.Scope.ALL, null, "Service", "All services")));

        assertThat(rows.getFirst().get(ImportField.ITEM_TYPE)).isEqualTo("Service");
        assertThat(rows.getFirst().get(ImportField.TRACK_INVENTORY)).isEqualTo("No");
        assertThat(rows.getFirst().originOf(ImportField.TRACK_INVENTORY).resolution())
                .isEqualTo(FieldResolutionSource.DERIVED);
    }

    /** Running it twice over the same rows must reach the same place. */
    @Test
    void reachesTheSameAnswerWhenRunAgain() {
        List<SourceRow> source = List.of(
                row(2, Map.of("name", "Rice", "cat", "Dry", "uom", "kg", "kind", "physical")));
        List<FieldRule> rules = List.of(new FieldRule(
                ImportField.TRACK_INVENTORY, FieldRule.Scope.ALL, null, "Yes", "Operator"));

        assertThat(rowsAfter(source, rules).getFirst().values())
                .isEqualTo(rowsAfter(source, rules).getFirst().values());
    }

    /**
     * A stock-only migration does not have to describe items it is not creating.
     *
     * When the shop already has the catalogue, the source only has to say which
     * item and how many. Demanding a name, a unit and an item type from a file
     * whose whole purpose is to update quantities would block a migration for
     * data the target already holds.
     */
    @Test
    void doesNotDemandItemDetailsFromAStockOnlyMigration() {
        Map<String, ImportField> stockMapping = Map.of(
                "code", ImportField.SKU,
                "qty", ImportField.OPENING_STOCK);

        TransformResult result = transformer.transform(
                List.of(row(2, new LinkedHashMap<>(Map.of("code", "P001", "qty", "40")))),
                stockMapping,
                ImportTargetType.OPENING_STOCK,
                Map.of());

        MissingFieldResolutionService.Outcome outcome =
                resolver.resolve(result.rows(), ImportTargetType.OPENING_STOCK, List.of());

        assertThat(outcome.findings()).isEmpty();
        assertThat(outcome.report().fields())
                .noneMatch(status -> status.field() == ImportField.UNIT
                        || status.field() == ImportField.NAME);
    }
}
