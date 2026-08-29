package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.migration.entity.AssistedMigrationSource;
import kh.edu.istad.ite.features.migration.entity.MigrationSourceRelationship;
import kh.edu.istad.ite.features.migration.join.JoinAnalysisService;
import kh.edu.istad.ite.features.migration.join.JoinQuality;
import kh.edu.istad.ite.features.migration.join.JoinSuggestion;
import kh.edu.istad.ite.features.migration.join.JoinSuggestionService;
import kh.edu.istad.ite.features.migration.join.SourceJoiner;
import kh.edu.istad.ite.features.migration.resolve.ResolvedRecord;
import kh.edu.istad.ite.shared.enums.FieldResolutionSource;
import kh.edu.istad.ite.shared.enums.JoinCardinality;
import kh.edu.istad.ite.shared.enums.MigrationJoinType;

/**
 * Several files, one catalogue.
 *
 * A customer's data arrives in pieces because that is what their old system
 * would give them: a product list, a stock count from a handheld scanner, a
 * price sheet somebody keeps by hand. Joining them is the work we are doing on
 * their behalf, and the thing that must never happen while doing it is a
 * confident wrong match — one product's stock landing on another's, with
 * nothing on screen to say so.
 */
class SourceJoinTest {

    private final JoinAnalysisService analysis = new JoinAnalysisService();
    private final JoinSuggestionService suggestions = new JoinSuggestionService(analysis);
    private final SourceJoiner joiner = new SourceJoiner();

    private AssistedMigrationSource source(
            String fileName,
            int ordinal,
            List<String> columns,
            Map<String, ImportField> mapping
    ) {
        AssistedMigrationSource source = new AssistedMigrationSource();
        Map<String, String> mappings = new LinkedHashMap<>();

        mapping.forEach((heading, field) -> mappings.put(heading, field.name()));

        source.setId(UUID.randomUUID());
        source.setFileName(fileName);
        source.setOrdinal(ordinal);
        source.setSourceColumns(columns);
        source.setColumnMappings(mappings);

        return source;
    }

    private SourceRow row(int number, Map<String, String> cells) {
        return new SourceRow(number, new LinkedHashMap<>(cells));
    }

    private MigrationSourceRelationship link(
            AssistedMigrationSource left,
            String leftColumn,
            AssistedMigrationSource right,
            String rightColumn,
            MigrationJoinType type
    ) {
        MigrationSourceRelationship relationship = new MigrationSourceRelationship();

        relationship.setLeftSourceId(left.getId());
        relationship.setLeftColumn(leftColumn);
        relationship.setRightSourceId(right.getId());
        relationship.setRightColumn(rightColumn);
        relationship.setJoinType(type);

        return relationship;
    }

    private AssistedMigrationSource products() {
        return source("products.csv", 0,
                List.of("product_code", "name", "uom"),
                Map.of("product_code", ImportField.SKU,
                        "name", ImportField.NAME,
                        "uom", ImportField.UNIT));
    }

    private AssistedMigrationSource stock() {
        return source("stock.csv", 1,
                List.of("product_code", "quantity"),
                Map.of("quantity", ImportField.OPENING_STOCK));
    }

    private List<SourceRow> productRows() {
        return List.of(
                row(2, Map.of("product_code", "P001", "name", "Coca Cola", "uom", "can")),
                row(3, Map.of("product_code", "P002", "name", "Rice", "uom", "kg")));
    }

    private List<SourceRow> stockRows() {
        return List.of(
                row(2, Map.of("product_code", "P001", "quantity", "100")),
                row(3, Map.of("product_code", "P002", "quantity", "40")));
    }

    /**
     * The quantity was only ever in the other file.
     *
     * This is the scenario the whole adjustment exists for: a stock export
     * holding nothing but a code and a number is useless alone and complete
     * once it is joined to the product list.
     */
    @Test
    void bringsAQuantityAcrossFromTheStockFile() {
        AssistedMigrationSource products = products();
        AssistedMigrationSource stock = stock();

        SourceJoiner.JoinedRecords joined = joiner.join(
                products,
                List.of(products, stock),
                Map.of(products.getId(), productRows(), stock.getId(), stockRows()),
                List.of(link(products, "product_code", stock, "product_code", MigrationJoinType.LEFT)));

        ResolvedRecord first = joined.records().getFirst();

        assertThat(first.get(ImportField.NAME)).isEqualTo("Coca Cola");
        assertThat(first.get(ImportField.OPENING_STOCK)).isEqualTo("100");
        assertThat(first.provenanceOf(ImportField.OPENING_STOCK).resolution())
                .isEqualTo(FieldResolutionSource.JOINED_SOURCE);
        assertThat(first.provenanceOf(ImportField.OPENING_STOCK).sourceFile()).isEqualTo("stock.csv");
    }

    /**
     * The main file wins, because it is the record's own account of itself.
     *
     * A price sheet that has gone stale must not overwrite what the catalogue
     * says. The order values are offered in is the priority, so this holds
     * without anything having to compare them.
     */
    @Test
    void keepsWhatTheMainFileSaidOverWhatAJoinedFileSays() {
        AssistedMigrationSource products = products();
        AssistedMigrationSource prices = source("prices.csv", 1,
                List.of("product_code", "uom"),
                Map.of("uom", ImportField.UNIT));

        SourceJoiner.JoinedRecords joined = joiner.join(
                products,
                List.of(products, prices),
                Map.of(
                        products.getId(), productRows(),
                        prices.getId(), List.of(row(2, Map.of("product_code", "P001", "uom", "bottle")))),
                List.of(link(products, "product_code", prices, "product_code", MigrationJoinType.LEFT)));

        assertThat(joined.records().getFirst().get(ImportField.UNIT)).isEqualTo("can");
        assertThat(joined.records().getFirst().provenanceOf(ImportField.UNIT).resolution())
                .isEqualTo(FieldResolutionSource.DIRECT_SOURCE);
    }

    /**
     * A product the stock file never mentions is still a product.
     *
     * Reported rather than dropped: 150 products without a stock line is worth
     * knowing and is not a reason to refuse them.
     */
    @Test
    void keepsProductsTheOtherFileDoesNotMentionAndSaysSo() {
        AssistedMigrationSource products = products();
        AssistedMigrationSource stock = stock();

        SourceJoiner.JoinedRecords joined = joiner.join(
                products,
                List.of(products, stock),
                Map.of(
                        products.getId(), productRows(),
                        stock.getId(), List.of(row(2, Map.of("product_code", "P001", "quantity", "100")))),
                List.of(link(products, "product_code", stock, "product_code", MigrationJoinType.LEFT)));

        assertThat(joined.records()).hasSize(2);
        assertThat(joined.records().get(1).get(ImportField.OPENING_STOCK)).isNull();
        assertThat(joined.findings()).anyMatch(f -> f.code().equals("SOURCE_NOT_MATCHED"));
    }

    /**
     * A join is not a filter unless somebody asked for one.
     *
     * An inner join is right when the second file defines the scope, and it
     * has to be chosen — defaulting to it would silently shrink a catalogue.
     */
    @Test
    void dropsUnmatchedRecordsOnlyWhenTheJoinSaysTo() {
        AssistedMigrationSource products = products();
        AssistedMigrationSource stock = stock();

        SourceJoiner.JoinedRecords joined = joiner.join(
                products,
                List.of(products, stock),
                Map.of(
                        products.getId(), productRows(),
                        stock.getId(), List.of(row(2, Map.of("product_code", "P001", "quantity", "100")))),
                List.of(link(products, "product_code", stock, "product_code", MigrationJoinType.INNER)));

        assertThat(joined.records()).hasSize(1);
        assertThat(joined.records().getFirst().get(ImportField.NAME)).isEqualTo("Coca Cola");
    }

    /** Codes are the same code whichever case the two systems wrote them in. */
    @Test
    void matchesCodesThatDifferOnlyInCase() {
        AssistedMigrationSource products = products();
        AssistedMigrationSource stock = stock();

        SourceJoiner.JoinedRecords joined = joiner.join(
                products,
                List.of(products, stock),
                Map.of(
                        products.getId(), productRows(),
                        stock.getId(), List.of(row(2, Map.of("product_code", "p001", "quantity", "100")))),
                List.of(link(products, "product_code", stock, "product_code", MigrationJoinType.LEFT)));

        assertThat(joined.records().getFirst().get(ImportField.OPENING_STOCK)).isEqualTo("100");
    }

    /**
     * Two columns of codes that mostly agree are a relationship worth offering.
     */
    @Test
    void suggestsTheColumnThatIdentifiesTheSameRecords() {
        AssistedMigrationSource products = products();
        AssistedMigrationSource stock = stock();

        List<JoinSuggestion> found = suggestions.suggest(
                products,
                List.of(stock),
                Map.of(products.getId(), productRows(), stock.getId(), stockRows()));

        assertThat(found).isNotEmpty();
        assertThat(found.getFirst().leftColumn()).isEqualTo("product_code");
        assertThat(found.getFirst().rightColumn()).isEqualTo("product_code");
        assertThat(found.getFirst().isHigh()).isTrue();
    }

    /**
     * Never on the name, however well the names line up.
     *
     * Two shops both sell "Water"; one export spells it "WATER 500ML" and the
     * other "Water 500 ml". A join on names misses real matches and makes
     * false ones, and it does both silently.
     */
    @Test
    void refusesToSuggestJoiningOnNames() {
        AssistedMigrationSource left = source("a.csv", 0,
                List.of("name"), Map.of("name", ImportField.NAME));
        AssistedMigrationSource right = source("b.csv", 1,
                List.of("name"), Map.of());

        List<JoinSuggestion> found = suggestions.suggest(
                left,
                List.of(right),
                Map.of(
                        left.getId(), List.of(
                                row(2, Map.of("name", "Coca Cola")),
                                row(3, Map.of("name", "Rice"))),
                        right.getId(), List.of(
                                row(2, Map.of("name", "Coca Cola")),
                                row(3, Map.of("name", "Rice")))));

        assertThat(found).isEmpty();
    }

    /**
     * A key repeating on both sides cannot be carried out honestly.
     *
     * There is no way to say which left row a repeated right row belongs to,
     * so the join would multiply rows — the shop would end up with more items
     * than they sent us and no way to tell which are real.
     */
    @Test
    void refusesAJoinThatWouldMultiplyRows() {
        JoinQuality quality = analysis.analyse(
                List.of(
                        row(2, Map.of("code", "A")),
                        row(3, Map.of("code", "A"))),
                "code",
                List.of(
                        row(2, Map.of("code", "A")),
                        row(3, Map.of("code", "A"))),
                "code");

        assertThat(quality.cardinality()).isEqualTo(JoinCardinality.MANY_TO_MANY);
        assertThat(quality.isUsable()).isFalse();
    }

    /** Several stock lines for one product is ordinary, and worth saying. */
    @Test
    void reportsWhenTheOtherFileHasSeveralRowsPerRecord() {
        JoinQuality quality = analysis.analyse(
                List.of(row(2, Map.of("code", "A")), row(3, Map.of("code", "B"))),
                "code",
                List.of(
                        row(2, Map.of("code", "A")),
                        row(3, Map.of("code", "A")),
                        row(4, Map.of("code", "B"))),
                "code");

        assertThat(quality.cardinality()).isEqualTo(JoinCardinality.ONE_TO_MANY);
        assertThat(quality.isUsable()).isTrue();
        assertThat(quality.duplicateRightKeys()).isEqualTo(1);
    }

    /** The counts an operator reads before approving anything. */
    @Test
    void countsWhatAJoinWouldReachAndWhatItWouldMiss() {
        JoinQuality quality = analysis.analyse(
                List.of(
                        row(2, Map.of("code", "A")),
                        row(3, Map.of("code", "B")),
                        row(4, Map.of("code", "C"))),
                "code",
                List.of(
                        row(2, Map.of("code", "A")),
                        row(3, Map.of("code", "Z"))),
                "code");

        assertThat(quality.matchedLeftRows()).isEqualTo(1);
        assertThat(quality.unmatchedLeftRows()).isEqualTo(2);
        assertThat(quality.unmatchedRightRows()).isEqualTo(1);
    }

    /** A migration with one file joins nothing and reads exactly as before. */
    @Test
    void leavesASingleFileMigrationAlone() {
        AssistedMigrationSource products = products();

        SourceJoiner.JoinedRecords joined = joiner.join(
                products, List.of(products), Map.of(products.getId(), productRows()), List.of());

        assertThat(joined.records()).hasSize(2);
        assertThat(joined.findings()).isEmpty();
        assertThat(joined.records().getFirst().provenanceOf(ImportField.NAME).resolution())
                .isEqualTo(FieldResolutionSource.DIRECT_SOURCE);
    }

    /** Joining twice must not add records or findings the first run did not. */
    @Test
    void reachesTheSameAnswerWhenRunAgain() {
        AssistedMigrationSource products = products();
        AssistedMigrationSource stock = stock();

        Map<UUID, List<SourceRow>> rows =
                Map.of(products.getId(), productRows(), stock.getId(), stockRows());
        List<MigrationSourceRelationship> links =
                List.of(link(products, "product_code", stock, "product_code", MigrationJoinType.LEFT));

        List<String> first = new ArrayList<>();
        List<String> again = new ArrayList<>();

        joiner.join(products, List.of(products, stock), rows, links).records()
                .forEach(record -> first.add(record.fields().toString()));
        joiner.join(products, List.of(products, stock), rows, links).records()
                .forEach(record -> again.add(record.fields().toString()));

        assertThat(again).isEqualTo(first);
    }

    /**
     * A unit the main file never had, supplied by the file that did.
     *
     * The reason joining happens before anything is called missing. Asked
     * first, this record has no unit and would become a blocking question; the
     * price sheet answers it, and nobody has to be interrupted.
     */
    @Test
    void resolvesAUnitTheMainFileNeverHad() {
        AssistedMigrationSource stockFirst = source("stock.csv", 0,
                List.of("product_code", "quantity"),
                Map.of("product_code", ImportField.SKU,
                        "quantity", ImportField.OPENING_STOCK));

        AssistedMigrationSource catalogue = source("products.csv", 1,
                List.of("product_code", "name", "uom"),
                Map.of("name", ImportField.NAME, "uom", ImportField.UNIT));

        SourceJoiner.JoinedRecords joined = joiner.join(
                stockFirst,
                List.of(stockFirst, catalogue),
                Map.of(stockFirst.getId(), stockRows(), catalogue.getId(), productRows()),
                List.of(link(stockFirst, "product_code", catalogue, "product_code",
                        MigrationJoinType.LEFT)));

        ResolvedRecord first = joined.records().getFirst();

        assertThat(first.get(ImportField.UNIT)).isEqualTo("can");
        assertThat(first.get(ImportField.NAME)).isEqualTo("Coca Cola");
        assertThat(first.get(ImportField.OPENING_STOCK)).isEqualTo("100");
        assertThat(first.provenanceOf(ImportField.UNIT).resolution())
                .isEqualTo(FieldResolutionSource.JOINED_SOURCE);
    }
}
