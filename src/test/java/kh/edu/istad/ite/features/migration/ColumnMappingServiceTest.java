package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.migration.mapping.ColumnMappingService;
import kh.edu.istad.ite.features.migration.mapping.ColumnSuggestion;
import kh.edu.istad.ite.features.migration.profile.SourceProfilingService;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

/**
 * A stranger's export, read the way an operator would have to read it.
 *
 * The file in these tests is the one from the specification — a real POS
 * export's headings, which say nothing to anyone who did not write them. What
 * matters is not that every column is matched, but that the ones we claim to
 * recognise are right: an operator can map a column we left alone, and will
 * not notice one we mapped confidently and wrongly.
 */
class ColumnMappingServiceTest {

    private final SourceProfilingService profiler = new SourceProfilingService();
    private final ColumnMappingService mapper = new ColumnMappingService();

    private static final List<String> COLUMNS = List.of(
            "prd_code", "prd_desc", "cat", "sub_cat", "uom", "sell_p", "cost", "balance", "track_qty");

    private SourceRow row(int number, String... values) {
        Map<String, String> cells = new LinkedHashMap<>();

        for (int index = 0; index < COLUMNS.size(); index++) {
            cells.put(COLUMNS.get(index), values[index]);
        }

        return new SourceRow(number, cells);
    }

    private Map<String, ImportField> mapped() {
        List<SourceRow> rows = List.of(
                row(2, "P001", "Coca Cola 330ml", "Drinks", "Soft Drinks", "CAN", "0.75", "0.40", "120", "Y"),
                row(3, "P002", "Sourdough Loaf", "Bakery", "Bread", "EA", "3.50", "1.20", "18", "Y"),
                row(4, "P003", "Espresso Beans 1kg", "Drinks", "Coffee", "KG", "12.50", "7.20", "40", "Y"),
                row(5, "P004", "Gift Wrapping", "Services", "Extras", "EA", "2.50", "0.00", "0", "N"),
                row(6, "P005", "Oat Milk 1L", "Drinks", "Dairy", "EA", "3.75", "2.10", "24", "Y"));

        SourceProfilingService.SourceProfile profile = profiler.profile(COLUMNS, rows);
        List<ColumnSuggestion> suggestions = mapper.suggest(profile.columns(), ImportTargetType.ITEM);

        Map<String, ImportField> byColumn = new LinkedHashMap<>();
        suggestions.forEach(s -> byColumn.put(s.sourceColumn(), s.target()));

        return byColumn;
    }

    /** The four the specification calls out by name. */
    @Test
    void readsAnUnfamiliarPosExport() {
        Map<String, ImportField> mapped = mapped();

        assertThat(mapped).containsEntry("prd_desc", ImportField.NAME);
        assertThat(mapped).containsEntry("sell_p", ImportField.PRICE);
        assertThat(mapped).containsEntry("uom", ImportField.UNIT);
        assertThat(mapped).containsEntry("balance", ImportField.OPENING_STOCK);
    }

    @Test
    void readsTheRestOfTheColumnsItRecognises() {
        Map<String, ImportField> mapped = mapped();

        assertThat(mapped).containsEntry("prd_code", ImportField.SKU);
        assertThat(mapped).containsEntry("cost", ImportField.COST_PRICE);
        assertThat(mapped).containsEntry("track_qty", ImportField.TRACK_INVENTORY);
    }

    /**
     * One field, one column. Both "cat" and "sub_cat" look like categories, and
     * mapping both to Category would quietly drop one — so the stronger takes
     * it and the other is left for the operator to place.
     */
    @Test
    void givesEachFluxibizFieldToAtMostOneColumn() {
        List<ImportField> targets = mapped().values().stream().toList();

        assertThat(targets).doesNotHaveDuplicates();
    }

    /**
     * A heading can be a coincidence; the values rarely are. A column called
     * "balance" holding names is not a stock count.
     */
    @Test
    void refusesAHeadingThatItsValuesContradict() {
        List<String> columns = List.of("balance");
        List<SourceRow> rows = List.of(
                new SourceRow(2, Map.of("balance", "Coca Cola")),
                new SourceRow(3, Map.of("balance", "Sourdough Loaf")),
                new SourceRow(4, Map.of("balance", "Oat Milk")));

        List<ColumnSuggestion> suggestions = mapper.suggest(
                profiler.profile(columns, rows).columns(), ImportTargetType.ITEM);

        assertThat(suggestions)
                .noneMatch(s -> s.target() == ImportField.OPENING_STOCK && s.isHigh());
    }

    /** A column nothing recognises is left alone rather than guessed at. */
    @Test
    void leavesAColumnItCannotNameToTheOperator() {
        List<String> columns = List.of("xq_ref9");
        List<SourceRow> rows = List.of(new SourceRow(2, Map.of("xq_ref9", "AB-1")));

        assertThat(mapper.suggest(profiler.profile(columns, rows).columns(), ImportTargetType.ITEM))
                .isEmpty();
    }

    /** Every suggestion has to be able to explain itself to whoever accepts it. */
    @Test
    void saysWhyItThinksSo() {
        List<ColumnSuggestion> suggestions = mapper.suggest(
                profiler.profile(List.of("uom"),
                        List.of(new SourceRow(2, Map.of("uom", "CAN")))).columns(),
                ImportTargetType.ITEM);

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.getFirst().reason()).contains("uom");
        assertThat(suggestions.getFirst().confidence()).isBetween(0.0, 1.0);
    }
}
