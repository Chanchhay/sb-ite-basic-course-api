package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.canonical.CanonicalRecordMapper;
import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.canonical.ItemImportRecord;
import kh.edu.istad.ite.features.dataimport.canonical.MappingPlan;
import kh.edu.istad.ite.features.dataimport.canonical.UnitSheetReader;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.parser.XlsxSourceFileParser;
import kh.edu.istad.ite.features.dataimport.validation.ItemImportValidator;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import kh.edu.istad.ite.features.dataimport.validation.RowVerdict;
import kh.edu.istad.ite.features.dataimport.validation.ValidationContext;
import kh.edu.istad.ite.features.migration.normalize.DataNormalizationService;
import kh.edu.istad.ite.features.migration.transform.PreparedWorkbookWriter;
import kh.edu.istad.ite.features.migration.transform.SourceTransformer;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.UnitCategory;

/**
 * A stranger's export, all the way to rows the importer accepts.
 *
 * The handover is the one seam where this feature could go quietly wrong: the
 * migration could produce a workbook it is perfectly happy with and the
 * importer could refuse every row of it, and neither side's own tests would
 * notice. So this drives a real POS export through the migration and then
 * through the importer's *own* reader, matcher, mapper and validator — the same
 * classes staging uses — and asserts the rows come out valid.
 *
 * It stops short of the database, which the test profile deliberately does not
 * provide. What it proves is the contract between the two features; what it
 * cannot prove is that JPA persists the result, and nothing else in this suite
 * proves that either.
 */
class HandoffIntegrationTest {

    private final SourceTransformer transformer = new SourceTransformer(new DataNormalizationService());
    private final PreparedWorkbookWriter writer = new PreparedWorkbookWriter();
    private final XlsxSourceFileParser parser = new XlsxSourceFileParser();
    private final UnitSheetReader unitReader = new UnitSheetReader();
    private final CanonicalRecordMapper canonical = new CanonicalRecordMapper();
    private final ItemImportValidator validator = new ItemImportValidator();

    private static final List<String> COLUMNS =
            List.of("prd_code", "prd_desc", "cat", "uom", "sell_p", "cost", "balance", "track_qty");

    private SourceRow source(int number, String... values) {
        Map<String, String> cells = new LinkedHashMap<>();

        for (int index = 0; index < COLUMNS.size(); index++) {
            cells.put(COLUMNS.get(index), values[index]);
        }

        return new SourceRow(number, cells);
    }

    private static final Map<String, ImportField> MAPPING = Map.of(
            "prd_code", ImportField.SKU,
            "prd_desc", ImportField.NAME,
            "cat", ImportField.ITEM_GROUP,
            "uom", ImportField.UNIT,
            "sell_p", ImportField.PRICE,
            "cost", ImportField.COST_PRICE,
            "balance", ImportField.OPENING_STOCK,
            "track_qty", ImportField.TRACK_INVENTORY);

    /**
     * The whole point: what assisted migration hands over is a file the shop's
     * own importer reads, matches and accepts without a single hand-written
     * mapping.
     */
    @Test
    void preparedDataPassesTheImportersOwnValidation() {
        // An operator has decided that a sack is a mass. Everything else is
        // either already known or plainly readable.
        Map<String, DeclaredUnit> decisions = Map.of(
                ImportField.UNIT.name() + "|sack",
                new DeclaredUnit("Sack", "sack", UnitCategory.MASS, "From SACK"));

        TransformResult prepared = transformer.transform(
                List.of(
                        source(2, "P001", "Coca Cola 330ml", "Drinks", "CAN", "$0.75", "0.40", "120", "Y"),
                        source(3, "P002", "Sourdough Loaf", "Bakery", "EA", "3.50", "1.20", "18", "Y"),
                        source(4, "P003", "Jasmine Rice", "Groceries", "SACK", "42.00", "30.00", "9", "Y"),
                        source(5, "P004", "Gift Wrapping", "Services", "EA", "2.50", "0", "", "N")),
                MAPPING,
                ImportTargetType.ITEM,
                decisions);

        assertThat(prepared.findings())
                .as("nothing should still be blocking once the sack is decided")
                .noneMatch(TransformResult.Finding::blocking);

        byte[] workbook = writer.write(
                writer.shapeFor(ImportTargetType.ITEM, prepared.rows()),
                prepared.rows(),
                prepared.units());

        // --- from here on, only the importer's own classes are used ------------

        var header = parser.readHeader(new ByteArrayInputStream(workbook), 20);

        Map<String, ImportField> matched =
                ImportField.suggestAll(header.columns(), ImportTargetType.ITEM);

        assertThat(matched.values())
                .as("every heading we wrote must be one the importer recognises")
                .hasSameSizeAs(header.columns());

        List<DeclaredUnit> declared =
                unitReader.read(parser, new ByteArrayInputStream(workbook));

        assertThat(declared)
                .as("the units the file needs travel with it")
                .extracting(DeclaredUnit::name)
                .contains("Can", "Piece", "Sack");

        Map<ImportField, String> byField = new EnumMap<>(ImportField.class);
        matched.forEach((column, field) -> byField.put(field, column));

        MappingPlan plan = new MappingPlan(
                ImportTargetType.ITEM, byField, ImportDuplicateStrategy.SKIP, null, null);

        // A brand-new shop: no units, no categories, nothing to collide with.
        ValidationContext context = new ValidationContext(
                UUID.randomUUID(), List.of(), List.of(), List.of(), Set.of(), Set.of(), declared);

        List<SourceRow> readBack = new ArrayList<>();
        parser.readRows(new ByteArrayInputStream(workbook), 100, readBack::add);

        assertThat(readBack).hasSize(4);

        for (SourceRow row : readBack) {
            var mapped = canonical.map(row, plan);

            assertThat(mapped.issues())
                    .as("row %s should read cleanly", row.rowNumber())
                    .noneMatch(RowIssue::isError);

            RowVerdict verdict = validator.validate(
                    (ItemImportRecord) mapped.record(), row.rowNumber(), context, plan);

            assertThat(verdict.status())
                    .as("row %s: %s", row.rowNumber(),
                            verdict.issues().stream().map(RowIssue::message).toList())
                    .isEqualTo(ImportRowStatus.VALID);
        }
    }

    /**
     * The unit an operator decided has to reach the importer as a real
     * declaration, or every row counted in it fails at the far end for a
     * question that was already answered.
     */
    @Test
    void carriesAnOperatorsUnitDecisionThroughToValidation() {
        TransformResult prepared = transformer.transform(
                List.of(source(2, "P001", "Jasmine Rice", "Groceries", "SACK", "42.00", "30.00", "9", "Y")),
                MAPPING,
                ImportTargetType.ITEM,
                Map.of(ImportField.UNIT.name() + "|sack",
                        new DeclaredUnit("Sack", "sack", UnitCategory.MASS, null)));

        byte[] workbook = writer.write(
                writer.shapeFor(ImportTargetType.ITEM, prepared.rows()),
                prepared.rows(),
                prepared.units());

        List<DeclaredUnit> declared = unitReader.read(parser, new ByteArrayInputStream(workbook));

        assertThat(declared)
                .singleElement()
                .satisfies(unit -> {
                    assertThat(unit.name()).isEqualTo("Sack");
                    assertThat(unit.category()).isEqualTo(UnitCategory.MASS);
                });
    }

    /**
     * A file the operator has not finished with must not be able to reach the
     * importer looking finished. An unknown unit leaves the row without one,
     * and the importer refuses it — which is the safety net behind the review
     * step rather than a substitute for it.
     */
    @Test
    void anUndecidedUnitIsStillRefusedAtTheFarEnd() {
        TransformResult prepared = transformer.transform(
                List.of(source(2, "P001", "Jasmine Rice", "Groceries", "SACK", "42.00", "30.00", "9", "Y")),
                MAPPING,
                ImportTargetType.ITEM,
                Map.of());

        assertThat(prepared.findings()).anyMatch(TransformResult.Finding::blocking);

        byte[] workbook = writer.write(
                writer.shapeFor(ImportTargetType.ITEM, prepared.rows()),
                prepared.rows(),
                prepared.units());

        var header = parser.readHeader(new ByteArrayInputStream(workbook), 10);
        Map<String, ImportField> matched =
                ImportField.suggestAll(header.columns(), ImportTargetType.ITEM);
        Map<ImportField, String> byField = new EnumMap<>(ImportField.class);
        matched.forEach((column, field) -> byField.put(field, column));

        MappingPlan plan = new MappingPlan(
                ImportTargetType.ITEM, byField, ImportDuplicateStrategy.SKIP, null, null);
        ValidationContext context = new ValidationContext(
                UUID.randomUUID(), List.of(), List.of(), List.of(), Set.of(), Set.of(), List.of());

        List<SourceRow> readBack = new ArrayList<>();
        parser.readRows(new ByteArrayInputStream(workbook), 10, readBack::add);

        var mapped = canonical.map(readBack.getFirst(), plan);
        RowVerdict verdict = validator.validate(
                (ItemImportRecord) mapped.record(), 2, context, plan);

        assertThat(verdict.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(verdict.issues()).anyMatch(issue -> issue.code().equals("MISSING_UNIT"));
    }
}
