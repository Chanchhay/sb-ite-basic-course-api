package kh.edu.istad.ite.features.dataimport.field;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import kh.edu.istad.ite.shared.enums.ImportTargetType;

class ImportFieldTest {

    /** However a shop writes a heading, it should find the same field. */
    @Test
    void suggestsTheSameFieldWhateverTheHeadingLooksLike() {
        for (String heading : List.of("product_name", "Product Name", "PRODUCT-NAME", "productName")) {
            assertThat(ImportField.suggestFor(heading, ImportTargetType.ITEM))
                    .as(heading)
                    .contains(ImportField.NAME);
        }
    }

    @Test
    void suggestsTheObviousCatalogueFields() {
        assertThat(ImportField.suggestFor("PRODUCT_CODE", ImportTargetType.ITEM)).contains(ImportField.SKU);
        assertThat(ImportField.suggestFor("Category", ImportTargetType.ITEM)).contains(ImportField.ITEM_GROUP);
        assertThat(ImportField.suggestFor("Sale Price", ImportTargetType.ITEM)).contains(ImportField.PRICE);
        assertThat(ImportField.suggestFor("QTY", ImportTargetType.ITEM)).contains(ImportField.OPENING_STOCK);
    }

    /** Better to leave a column unmatched than to match it to the wrong thing. */
    @Test
    void suggestsNothingForAHeadingItDoesNotKnow() {
        assertThat(ImportField.suggestFor("supplier_account_ref", ImportTargetType.ITEM)).isEmpty();
    }

    /**
     * Two columns that both look like the name must not both be matched to it,
     * or the user has to spot the clash before they can fix it.
     */
    @Test
    void givesEachFieldToAtMostOneColumn() {
        Map<String, ImportField> suggestions = ImportField.suggestAll(
                List.of("Item Name", "Product Name", "SKU"), ImportTargetType.ITEM);

        assertThat(suggestions).containsEntry("Item Name", ImportField.NAME);
        assertThat(suggestions).doesNotContainKey("Product Name");
        assertThat(suggestions).containsEntry("SKU", ImportField.SKU);
    }

    @Test
    void offersOnlyTheFieldsThatKindOfImportCanSet() {
        assertThat(ImportField.forTarget(ImportTargetType.ITEM_GROUP))
                .containsExactly(ImportField.NAME, ImportField.PARENT_GROUP, ImportField.NOTE);

        assertThat(ImportField.forTarget(ImportTargetType.ITEM)).contains(ImportField.PRICE);
        assertThat(ImportField.forTarget(ImportTargetType.OPENING_STOCK))
                .doesNotContain(ImportField.PARENT_GROUP);
    }

    /**
     * A file of items may describe both halves of a category.
     *
     * Without this an item can only ever be filed on a top-level category, so a
     * shop whose catalogue has sub-categories has nowhere to put anything: the
     * catalogue refuses items on a parent, and the file cannot name the child.
     */
    @Test
    void letsAFileOfItemsNameACategoryAndItsParent() {
        assertThat(ImportField.forTarget(ImportTargetType.ITEM))
                .contains(ImportField.ITEM_GROUP, ImportField.PARENT_GROUP);
    }

    /**
     * The narrower column wins the filing, whichever pair the file uses —
     * main and sub, category and sub, or parent and sub are all the same shape.
     */
    @ParameterizedTest
    @CsvSource({
            "Category, Sub-category",
            "Main Category, Sub Category",
            "Parent Category, Subcategory",
            "Main Group, Sub Group"
    })
    void readsACategoryPairTheRightWayRound(String top, String sub) {
        Map<String, ImportField> suggestions =
                ImportField.suggestAll(List.of("Item Name", top, sub), ImportTargetType.ITEM);

        assertThat(suggestions).containsEntry(sub, ImportField.ITEM_GROUP);
        assertThat(suggestions).containsEntry(top, ImportField.PARENT_GROUP);
    }

    /** One category column on its own is what it says it is. */
    @Test
    void leavesALoneCategoryColumnAsTheCategory() {
        Map<String, ImportField> suggestions =
                ImportField.suggestAll(List.of("Item Name", "Category"), ImportTargetType.ITEM);

        assertThat(suggestions).containsEntry("Category", ImportField.ITEM_GROUP);
        assertThat(suggestions).doesNotContainValue(ImportField.PARENT_GROUP);
    }

    @Test
    void knowsWhatEachKindOfImportInsistsOn() {
        assertThat(ImportField.requiredFor(ImportTargetType.ITEM))
                .containsExactly(ImportField.NAME, ImportField.ITEM_GROUP);

        assertThat(ImportField.requiredFor(ImportTargetType.ITEM_GROUP))
                .containsExactly(ImportField.NAME);

        assertThat(ImportField.identifiersFor(ImportTargetType.OPENING_STOCK))
                .containsExactly(ImportField.NAME, ImportField.SKU, ImportField.BARCODE);
    }

    /** The unit is required, but a choice for the whole file counts as matching it. */
    @Test
    void treatsTheUnitAsSatisfiableByADefault() {
        assertThat(ImportField.UNIT.requirementFor(ImportTargetType.ITEM))
                .isEqualTo(ImportFieldRequirement.REQUIRED_OR_DEFAULTED);
    }
}
