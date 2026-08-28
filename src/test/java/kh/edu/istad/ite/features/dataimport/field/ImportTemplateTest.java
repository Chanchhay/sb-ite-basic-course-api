package kh.edu.istad.ite.features.dataimport.field;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import kh.edu.istad.ite.features.dataimport.parser.SourceFileParser;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.dataimport.parser.XlsxSourceFileParser;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

/**
 * Every sample is held to the same promises, not one per kind of import.
 *
 * A shop picks the sample that sounds like their shop and never sees the
 * others, so a sample nobody checked is one that quietly fails for whoever
 * happened to choose it.
 *
 * Each is read back through the same reader an uploaded file goes through, so
 * these assertions are about the file a shop actually receives rather than the
 * lists it was built from.
 */
class ImportTemplateTest {

    private final XlsxSourceFileParser parser = new XlsxSourceFileParser();

    private SourceFileParser.SourceHeader read(ImportSample sample) {
        return parser.readHeader(new ByteArrayInputStream(ImportTemplate.xlsxFor(sample)), 20);
    }

    /**
     * The promise every sample makes: download it, fill it in, upload it, and
     * every column is already matched. A heading we wrote and then failed to
     * recognise would make the matching step look broken on the one file we had
     * complete control over.
     */
    @ParameterizedTest
    @EnumSource(ImportSample.class)
    void everySampleHeadingMatchesItsOwnField(ImportSample sample) {
        SourceFileParser.SourceHeader header = read(sample);
        List<ImportField> expected = sample.getColumns();

        assertThat(header.columns()).hasSameSizeAs(expected);

        for (int index = 0; index < expected.size(); index++) {
            assertThat(ImportField.suggestFor(header.columns().get(index), sample.getTargetType()))
                    .as("%s heading %s", sample, header.columns().get(index))
                    .contains(expected.get(index));
        }
    }

    /** Every column offered must be one that kind of import can actually set. */
    @ParameterizedTest
    @EnumSource(ImportSample.class)
    void offersOnlyFieldsThatKindOfImportAccepts(ImportSample sample) {
        assertThat(sample.getColumns())
                .allSatisfy(field -> assertThat(field.appliesTo(sample.getTargetType())).isTrue());
    }

    /** A sample missing a required column would fail the moment it was matched. */
    @ParameterizedTest
    @EnumSource(ImportSample.class)
    void includesEveryRequiredField(ImportSample sample) {
        assertThat(sample.getColumns())
                .containsAll(ImportField.requiredFor(sample.getTargetType()));
    }

    @Test
    void includesAnIdentifierForStockCounts() {
        assertThat(ImportSample.OPENING_STOCK_COUNTS.getColumns())
                .containsAnyElementsOf(ImportField.identifiersFor(ImportTargetType.OPENING_STOCK));
    }

    /**
     * A row shorter than the headings shifts every value after the gap into the
     * wrong column — exactly the mistake a sample exists to stop someone making.
     */
    @ParameterizedTest
    @EnumSource(ImportSample.class)
    void fillsEveryColumnOfEverySampleRow(ImportSample sample) {
        assertThat(sample.getRows())
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row).hasSameSizeAs(sample.getColumns()));
    }

    /**
     * Barcodes and SKUs are labels, not quantities. A workbook that decided
     * "8850001001" was a number would hand it back as 8.85E+09 the first time
     * anybody saved it.
     */
    @Test
    void keepsBarcodesAsTextRatherThanNumbers() {
        SourceFileParser.SourceHeader header = read(ImportSample.ITEMS);
        int barcodeAt = ImportSample.ITEMS.getColumns().indexOf(ImportField.BARCODE);

        SourceRow first = header.sample().getFirst();
        String barcode = first.value(header.columns().get(barcodeAt));

        assertThat(barcode).isEqualTo("8850001001");
    }

    /**
     * The sample that exists to explain options has to show one item spread
     * over several rows — a row per item would demonstrate the opposite.
     */
    @Test
    void showsOneItemAcrossSeveralRowsInTheOptionsSample() {
        List<List<String>> rows = ImportSample.ITEMS_WITH_OPTIONS.getRows();
        int groupKeyAt = ImportSample.ITEMS_WITH_OPTIONS.getColumns()
                .indexOf(ImportField.OPTION_GROUP_KEY);

        assertThat(groupKeyAt).isNotNegative();

        List<String> keys = rows.stream().map(row -> row.get(groupKeyAt)).toList();

        assertThat(keys).doesNotContain("");
        assertThat(keys.stream().distinct().count()).isLessThan(keys.size());
    }

    /**
     * Track Stock is what lets one items sample serve a shop that counts and one
     * that does not, so it has to actually show both.
     */
    @Test
    void showsBothTrackedAndUntrackedItems() {
        int trackAt = ImportSample.ITEMS.getColumns().indexOf(ImportField.TRACK_INVENTORY);

        assertThat(trackAt).isNotNegative();

        List<String> tracked = ImportSample.ITEMS.getRows().stream()
                .map(row -> row.get(trackAt))
                .toList();

        assertThat(tracked).contains("Yes", "No");
    }

    /**
     * Three files called fluxibiz-import-template are three files nobody can
     * tell apart in a downloads folder a week later.
     */
    @Test
    void givesEverySampleItsOwnName() {
        List<String> names = new ArrayList<>();

        for (ImportSample sample : ImportSample.values()) {
            names.add(ImportTemplate.fileNameFor(sample));
        }

        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allSatisfy(name -> assertThat(name).endsWith(".xlsx"));
    }

    /** Every kind of import must still offer at least one starting file. */
    @ParameterizedTest
    @EnumSource(ImportTargetType.class)
    void offersASampleForEveryKindOfImport(ImportTargetType targetType) {
        assertThat(ImportSample.forTarget(targetType)).isNotEmpty();
        assertThat(ImportSample.defaultFor(targetType).getTargetType()).isEqualTo(targetType);
    }
}
