package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.field.ImportSample;
import kh.edu.istad.ite.features.dataimport.parser.SourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.parser.XlsxSourceFileParser;
import kh.edu.istad.ite.features.migration.normalize.DataNormalizationService;
import kh.edu.istad.ite.features.migration.transform.PreparedRow;
import kh.edu.istad.ite.features.migration.transform.PreparedWorkbookWriter;
import kh.edu.istad.ite.features.migration.transform.SourceTransformer;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.UnitCategory;

/**
 * A stranger's file, read into FluxiBiz's words.
 *
 * The rule these tests hold is the one that makes a migration trustworthy:
 * everything unambiguous is done silently, and everything else becomes a
 * question. A migration that guesses is worse than one that asks, because a
 * wrong guess arrives looking exactly like a right one.
 */
class SourceTransformerTest {

    private final SourceTransformer transformer = new SourceTransformer(new DataNormalizationService());
    private final PreparedWorkbookWriter writer = new PreparedWorkbookWriter();

    private static final Map<String, ImportField> MAPPING = Map.of(
            "prd_desc", ImportField.NAME,
            "prd_code", ImportField.SKU,
            "sell_p", ImportField.PRICE,
            "uom", ImportField.UNIT,
            "balance", ImportField.OPENING_STOCK,
            "track_qty", ImportField.TRACK_INVENTORY,
            "cat", ImportField.ITEM_GROUP);

    private SourceRow row(int number, Map<String, String> cells) {
        return new SourceRow(number, new LinkedHashMap<>(cells));
    }

    private TransformResult run(List<SourceRow> rows) {
        return transformer.transform(rows, MAPPING, ImportTargetType.ITEM, Map.of());
    }

    private TransformResult run(List<SourceRow> rows, Map<String, DeclaredUnit> decisions) {
        return transformer.transform(rows, MAPPING, ImportTargetType.ITEM, decisions);
    }

    /** Money arrives dressed a dozen ways and means one thing. */
    @Test
    void readsPricesHoweverTheOldSystemWroteThem() {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "Coke", "sell_p", "$0.75", "uom", "CAN")),
                row(3, Map.of("prd_desc", "Pepsi", "sell_p", "0.75 USD", "uom", "CAN")),
                row(4, Map.of("prd_desc", "Fanta", "sell_p", ".75", "uom", "CAN")),
                row(5, Map.of("prd_desc", "Water", "sell_p", "1,200.50", "uom", "CAN"))));

        List<String> prices = result.rows().stream().map(r -> r.get(ImportField.PRICE)).toList();

        assertThat(prices).containsExactly("0.75", "0.75", "0.75", "1200.50");
    }

    /** Not a number is a question, never a zero. A free item is a different claim. */
    @Test
    void refusesToReadNonsenseAsZero() {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "Coke", "sell_p", "ask manager", "uom", "CAN"))));

        assertThat(result.rows().getFirst().get(ImportField.PRICE)).isNull();
        assertThat(result.findings())
                .anyMatch(f -> f.code().equals("VALUE_UNREADABLE") && f.blocking());
    }

    @Test
    void readsTheManyWaysOfSayingYes() {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "A", "track_qty", "Y", "uom", "CAN")),
                row(3, Map.of("prd_desc", "B", "track_qty", "YES", "uom", "CAN")),
                row(4, Map.of("prd_desc", "C", "track_qty", "1", "uom", "CAN")),
                row(5, Map.of("prd_desc", "D", "track_qty", "TRUE", "uom", "CAN"))));

        assertThat(result.rows().stream().map(r -> r.get(ImportField.TRACK_INVENTORY)).toList())
                .containsOnly("Yes");
    }

    /**
     * An unrecognised word is not a no. Reading "STK" as "do not count this"
     * would turn a shop's entire stocked catalogue into untracked items.
     */
    @Test
    void refusesToReadAnUnknownWordAsNo() {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "A", "track_qty", "STK", "uom", "CAN"))));

        assertThat(result.rows().getFirst().get(ImportField.TRACK_INVENTORY)).isNull();
        assertThat(result.findings()).anyMatch(f -> f.blocking());
    }

    /** CAN, Can and can are one unit, and saying so costs an operator nothing. */
    @Test
    void readsOneUnitHoweverItIsCapitalised() {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "A", "uom", "CAN")),
                row(3, Map.of("prd_desc", "B", "uom", "Can")),
                row(4, Map.of("prd_desc", "C", "uom", "can"))));

        assertThat(result.units()).hasSize(1);
        assertThat(result.units().getFirst().name()).isEqualTo("Can");
        assertThat(result.rows().stream().map(r -> r.get(ImportField.UNIT)).toList())
                .containsOnly("can");
    }

    /**
     * A sack is a count in most shops and a mass in some. Guessing wrong does
     * not announce itself — it changes what every quantity counted in it means.
     */
    @Test
    void asksAboutAUnitItDoesNotKnow() {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "Rice", "uom", "SACK")),
                row(3, Map.of("prd_desc", "Flour", "uom", "SACK"))));

        assertThat(result.findings())
                .filteredOn(f -> f.code().equals("UNIT_UNKNOWN"))
                .hasSize(2)
                .allSatisfy(f -> {
                    assertThat(f.blocking()).isTrue();
                    assertThat(f.sourceValue()).isEqualTo("SACK");
                });

        assertThat(result.rows()).allSatisfy(r -> assertThat(r.get(ImportField.UNIT)).isNull());
    }

    /**
     * The operator answers once and every row that asked is answered. This is
     * the difference between a review screen and a data-entry job.
     */
    @Test
    void appliesOneDecisionToEveryRowThatAskedIt() {
        Map<String, DeclaredUnit> decided = Map.of(
                ImportField.UNIT.name() + "|sack",
                new DeclaredUnit("Sack", "sack", UnitCategory.MASS, null));

        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "Rice", "uom", "SACK")),
                row(3, Map.of("prd_desc", "Flour", "uom", "SACK"))), decided);

        assertThat(result.findings()).noneMatch(f -> f.code().equals("UNIT_UNKNOWN"));
        assertThat(result.rows()).allSatisfy(r ->
                assertThat(r.get(ImportField.UNIT)).isEqualTo("sack"));
        assertThat(result.units()).singleElement()
                .satisfies(u -> assertThat(u.category()).isEqualTo(UnitCategory.MASS));
    }

    /**
     * Reading a file is not the same as judging it.
     *
     * A row with no name is a row this class has nothing to say about — the
     * name might yet arrive from a joined file, and deciding it is missing
     * before the other sources have been consulted would raise a question the
     * data already answers. That judgement belongs to
     * {@link kh.edu.istad.ite.features.migration.resolve.MissingFieldResolutionService},
     * which sees every source at once.
     */
    @Test
    void leavesTheVerdictOnAMissingNameToTheStepThatSeesEverySource() {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_code", "P001", "sell_p", "1.00", "uom", "CAN"))));

        assertThat(result.findings()).noneMatch(f -> f.code().equals("NAME_MISSING"));
        assertThat(result.rows().getFirst().get(ImportField.NAME)).isNull();
    }

    /** Whitespace is untidiness; a different name is a different product. */
    @Test
    void tidiesSpacingWithoutRenamingAnything() {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "   Coca   Cola   ", "uom", "CAN"))));

        assertThat(result.rows().getFirst().get(ImportField.NAME)).isEqualTo("Coca Cola");
    }

    /**
     * The handover has to be a file the importer already understands — read
     * back through the importer's own reader, not merely written.
     */
    @Test
    void handsOverAWorkbookTheImporterCanRead() throws Exception {
        TransformResult result = run(List.of(
                row(2, Map.of("prd_desc", "Coke", "prd_code", "P001", "cat", "Drinks",
                        "sell_p", "$0.75", "uom", "CAN", "balance", "120", "track_qty", "Y"))));

        byte[] workbook = writer.write(
                writer.shapeFor(ImportTargetType.ITEM, result.rows()), result.rows(), result.units());

        XlsxSourceFileParser parser = new XlsxSourceFileParser();
        SourceFileParser.SourceHeader header =
                parser.readHeader(new ByteArrayInputStream(workbook), 10);

        // Every heading is one the importer matches to the field it came from.
        for (String column : header.columns()) {
            assertThat(ImportField.suggestFor(column, ImportTargetType.ITEM))
                    .as("heading %s", column)
                    .isPresent();
        }

        List<SourceRow> readBack = new ArrayList<>();
        parser.readRows(new ByteArrayInputStream(workbook), 100, readBack::add);

        assertThat(readBack).singleElement().satisfies(row -> {
            assertThat(row.value("Name")).isEqualTo("Coke");
            assertThat(row.value("Selling Price")).isEqualTo("0.75");
            assertThat(row.value("Unit")).isEqualTo("can");
            assertThat(row.value("Opening Stock")).isEqualTo("120");
        });
    }

    /** The units an operator settled travel with the file, for the importer to create. */
    @Test
    void writesTheUnitsOntoTheWorkbooksOwnSheet() {
        TransformResult result = run(List.of(row(2, Map.of("prd_desc", "Coke", "uom", "CAN"))));

        byte[] workbook = writer.write(
                writer.shapeFor(ImportTargetType.ITEM, result.rows()), result.rows(), result.units());

        List<SourceRow> units = new XlsxSourceFileParser().readNamedSheet(
                new ByteArrayInputStream(workbook), XlsxSourceFileParser.UNITS_SHEET, 50);

        assertThat(units).singleElement().satisfies(row -> {
            assertThat(row.value("Name")).isEqualTo("Can");
            assertThat(row.value("Type")).isEqualTo("COUNT");
        });
    }

    /** A file carrying options goes in as options, not as one item per row. */
    @Test
    void choosesTheOptionsWorkbookWhenTheRowsCarryOptions() {
        PreparedRow plain = PreparedRow.empty(2);
        plain.put(ImportField.NAME, "Coke");

        PreparedRow option = PreparedRow.empty(3);
        option.put(ImportField.NAME, "T-Shirt");
        option.put(ImportField.OPTION_GROUP_KEY, "TS01");
        option.put(ImportField.OPTION_1_VALUE, "Small");

        assertThat(writer.shapeFor(ImportTargetType.ITEM, List.of(plain)))
                .isEqualTo(ImportSample.ITEMS);
        assertThat(writer.shapeFor(ImportTargetType.ITEM, List.of(plain, option)))
                .isEqualTo(ImportSample.ITEMS_WITH_OPTIONS);
    }

    private static final Map<String, ImportField> OPTION_MAPPING = Map.of(
            "PRODUCT_ID", ImportField.OPTION_GROUP_KEY,
            "NAME", ImportField.NAME,
            "SIZE", ImportField.OPTION_1_VALUE,
            "COLOR", ImportField.OPTION_2_VALUE,
            "SKU", ImportField.SKU,
            "uom", ImportField.UNIT);

    private TransformResult runOptions(List<SourceRow> rows, Map<String, String> axisNames) {
        return transformer.transform(
                rows, OPTION_MAPPING, ImportTargetType.ITEM, Map.of(), axisNames);
    }

    /**
     * Three rows sharing a real parent id are one shirt in three shelves. The
     * axis names come from the headings, because that is where the file keeps
     * the word FluxiBiz shows to shoppers.
     */
    @Test
    void rebuildsOptionsFromTheSourcesOwnParentKey() {
        TransformResult result = runOptions(List.of(
                row(2, Map.of("PRODUCT_ID", "TS01", "NAME", "T-Shirt", "SIZE", "Small",
                        "COLOR", "Black", "SKU", "TS-S-BLK", "uom", "pcs")),
                row(3, Map.of("PRODUCT_ID", "TS01", "NAME", "T-Shirt", "SIZE", "Medium",
                        "COLOR", "Black", "SKU", "TS-M-BLK", "uom", "pcs")),
                row(4, Map.of("PRODUCT_ID", "TS01", "NAME", "T-Shirt", "SIZE", "Small",
                        "COLOR", "Red", "SKU", "TS-S-RED", "uom", "pcs"))), Map.of());

        assertThat(result.rows()).hasSize(3);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.get(ImportField.OPTION_GROUP_KEY)).isEqualTo("TS01");
            assertThat(row.get(ImportField.OPTION_1_NAME)).isEqualTo("Size");
            assertThat(row.get(ImportField.OPTION_2_NAME)).isEqualTo("Color");
        });

        assertThat(result.rows().stream().map(r -> r.get(ImportField.OPTION_1_VALUE)).toList())
                .containsExactly("Small", "Medium", "Small");
    }

    /** A heading like "SZ" needs a person; the operator's word wins. */
    @Test
    void letsTheOperatorNameAnAxis() {
        TransformResult result = runOptions(List.of(
                row(2, Map.of("PRODUCT_ID", "TS01", "NAME", "T-Shirt", "SIZE", "Small",
                        "SKU", "TS-S", "uom", "pcs"))),
                Map.of("option1", "Garment Size"));

        assertThat(result.rows().getFirst().get(ImportField.OPTION_1_NAME))
                .isEqualTo("Garment Size");
    }

    /**
     * FluxiBiz keeps the unit on the item, so an item cannot be sold by the
     * piece in one size and by the kilogram in another.
     */
    @Test
    void refusesOptionRowsOfOneItemThatDisagreeAboutTheUnit() {
        TransformResult result = runOptions(List.of(
                row(2, Map.of("PRODUCT_ID", "TS01", "NAME", "T-Shirt", "SIZE", "Small",
                        "SKU", "TS-S", "uom", "pcs")),
                row(3, Map.of("PRODUCT_ID", "TS01", "NAME", "T-Shirt", "SIZE", "Large",
                        "SKU", "TS-L", "uom", "kg"))), Map.of());

        assertThat(result.findings())
                .filteredOn(f -> f.code().equals("UNIT_CONFLICT_IN_GROUP"))
                .singleElement()
                .satisfies(f -> assertThat(f.blocking()).isTrue());
    }

    /** Options that agree about the unit are the ordinary case. */
    @Test
    void acceptsOptionRowsThatShareOneUnit() {
        TransformResult result = runOptions(List.of(
                row(2, Map.of("PRODUCT_ID", "TS01", "NAME", "T-Shirt", "SIZE", "Small",
                        "SKU", "TS-S", "uom", "pcs")),
                row(3, Map.of("PRODUCT_ID", "TS01", "NAME", "T-Shirt", "SIZE", "Large",
                        "SKU", "TS-L", "uom", "pcs"))), Map.of());

        assertThat(result.findings()).noneMatch(f -> f.code().equals("UNIT_CONFLICT_IN_GROUP"));
    }

    /**
     * Running the same file twice must produce the same answer. An operator who
     * fixes one column and re-runs should not find the file read differently.
     */
    @Test
    void readsTheSameFileTheSameWayTwice() {
        List<SourceRow> rows = List.of(
                row(2, Map.of("prd_desc", "Coke", "sell_p", "$0.75", "uom", "CAN")),
                row(3, Map.of("prd_desc", "Rice", "sell_p", "2.00", "uom", "SACK")));

        TransformResult first = run(rows);
        TransformResult second = run(rows);

        assertThat(second.rows()).hasSameSizeAs(first.rows());
        assertThat(second.findings().stream().map(TransformResult.Finding::code).toList())
                .isEqualTo(first.findings().stream().map(TransformResult.Finding::code).toList());
    }
}
