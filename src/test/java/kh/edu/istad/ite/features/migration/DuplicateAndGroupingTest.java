package kh.edu.istad.ite.features.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.migration.duplicate.DuplicateDetectionService;
import kh.edu.istad.ite.features.migration.normalize.CategoryGrouping;
import kh.edu.istad.ite.features.migration.transform.PreparedRow;
import kh.edu.istad.ite.features.migration.transform.TransformResult;

/**
 * Two jobs that look alike and are not.
 *
 * A repeated SKU is a fact about the file. Two names that resemble each other
 * is an opinion about the shop's catalogue, and the difference decides what may
 * be done about each: the first can block, the second can only ask.
 */
class DuplicateAndGroupingTest {

    private final DuplicateDetectionService duplicates = new DuplicateDetectionService();
    private final CategoryGrouping categories = new CategoryGrouping();

    private PreparedRow row(int number, String name, String sku, String category) {
        PreparedRow row = PreparedRow.empty(number);

        row.put(ImportField.NAME, name);
        row.put(ImportField.SKU, sku);
        row.put(ImportField.ITEM_GROUP, category);

        return row;
    }

    /** The shop's own promise that two things are the same, broken. */
    @Test
    void reportsTheSameSkuOnTwoRows() {
        List<TransformResult.Finding> found = duplicates.findWithin(List.of(
                row(2, "Coke", "P001", "Drinks"),
                row(3, "Pepsi", "P001", "Drinks")));

        assertThat(found)
                .filteredOn(f -> f.code().equals("DUPLICATE_IDENTIFIER"))
                .singleElement()
                .satisfies(f -> assertThat(f.blocking()).isTrue());
    }

    /**
     * Names that look alike are raised and never merged — and never blocking,
     * because a shop may genuinely sell both.
     */
    @Test
    void raisesLookalikeNamesWithoutMergingThem() {
        List<TransformResult.Finding> found = duplicates.findWithin(List.of(
                row(2, "Coca Cola 330ML", "P001", "Drinks"),
                row(3, "Coca-Cola 330 ml", "P002", "Drinks"),
                row(4, "COCA COLA 330ML", "P003", "Drinks")));

        assertThat(found)
                .filteredOn(f -> f.code().equals("POSSIBLE_DUPLICATE"))
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.blocking()).isFalse());
    }

    /** Sharing a word is not being the same product. */
    @Test
    void leavesGenuinelyDifferentProductsAlone() {
        List<TransformResult.Finding> found = duplicates.findWithin(List.of(
                row(2, "Coffee", "P001", "Drinks"),
                row(3, "Coffee Equipment Cleaning Kit", "P002", "Supplies")));

        assertThat(found).noneMatch(f -> f.code().equals("POSSIBLE_DUPLICATE"));
    }

    /**
     * The comparison must not grow with the square of the file. Two thousand
     * rows sharing nothing should finish in the time one bucket takes.
     */
    @Test
    void staysQuickOnALargeCatalogue() {
        List<PreparedRow> rows = new ArrayList<>();

        for (int index = 0; index < 2_000; index++) {
            rows.add(row(index + 2, "Product " + index + " " + Integer.toHexString(index),
                    "SKU-" + index, "Drinks"));
        }

        long started = System.nanoTime();
        duplicates.findWithin(rows);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(millis).isLessThan(2_000);
    }

    /** One shelf written three ways is one shelf, offered as a suggestion. */
    @Test
    void groupsCategoriesThatDifferOnlyInSpelling() {
        List<TransformResult.Finding> found = categories.find(List.of(
                row(2, "Coke", "P001", "beverage"),
                row(3, "Pepsi", "P002", "Beverage"),
                row(4, "Fanta", "P003", "BEVERAGES")));

        assertThat(found)
                .filteredOn(f -> f.code().equals("CATEGORY_SPELLINGS"))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.blocking()).isFalse();
                    assertThat(f.message()).contains("3 ways");
                });
    }

    /** "Coffee" and "Coffee Equipment" are two shelves, and stay two. */
    @Test
    void keepsGenuinelyDifferentCategoriesApart() {
        List<TransformResult.Finding> found = categories.find(List.of(
                row(2, "Beans", "P001", "Coffee"),
                row(3, "Grinder", "P002", "Coffee Equipment")));

        assertThat(found).isEmpty();
    }

    /**
     * The half that matters on a second attempt: a re-run should say "you
     * already have these" rather than quietly making them again.
     */
    @Test
    void noticesItemsTheShopAlreadyHas() {
        var existing = DuplicateDetectionService.ExistingCatalogue.of(
                List.of("P001"), List.of("Coca Cola"));

        List<TransformResult.Finding> found = duplicates.findAgainstCatalogue(
                List.of(row(2, "Coca Cola", "P001", "Drinks")), existing);

        assertThat(found)
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.code()).isEqualTo("ALREADY_IN_CATALOGUE");
                    assertThat(f.blocking()).isFalse();
                });
    }

    /**
     * A name match without a code match is the ambiguous one — renamed product,
     * or two products sharing a name — so it is raised, not decided.
     */
    @Test
    void raisesANameMatchWithADifferentCode() {
        var existing = DuplicateDetectionService.ExistingCatalogue.of(
                List.of("OLD-1"), List.of("Coca Cola"));

        List<TransformResult.Finding> found = duplicates.findAgainstCatalogue(
                List.of(row(2, "Coca Cola", "NEW-1", "Drinks")), existing);

        assertThat(found)
                .singleElement()
                .satisfies(f -> assertThat(f.code()).isEqualTo("NAME_ALREADY_IN_CATALOGUE"));
    }

    /** The SKU already answered it; saying it twice is noise. */
    @Test
    void doesNotReportBothWhenTheCodeAlreadyMatched() {
        var existing = DuplicateDetectionService.ExistingCatalogue.of(
                List.of("P001"), List.of("Coca Cola"));

        List<TransformResult.Finding> found = duplicates.findAgainstCatalogue(
                List.of(row(2, "Coca Cola", "P001", "Drinks")), existing);

        assertThat(found).hasSize(1);
    }

    /** A shop with nothing in it has nothing to clash with. */
    @Test
    void saysNothingAboutAnEmptyCatalogue() {
        var existing = DuplicateDetectionService.ExistingCatalogue.of(List.of(), List.of());

        assertThat(duplicates.findAgainstCatalogue(
                List.of(row(2, "Coca Cola", "P001", "Drinks")), existing)).isEmpty();
    }
}
