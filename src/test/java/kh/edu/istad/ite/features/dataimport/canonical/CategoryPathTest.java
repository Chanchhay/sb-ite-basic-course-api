package kh.edu.istad.ite.features.dataimport.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A category cell says where an item goes, in whichever shape its old system
 * wrote it — one name, or the whole aisle leading to the shelf.
 */
class CategoryPathTest {

    @Test
    void readsAPlainCategoryAsItself() {
        CategoryPath path = CategoryPath.of("Coffee");

        assertThat(path.name()).isEqualTo("Coffee");
        assertThat(path.parent()).isNull();
    }

    @Test
    void readsATwoLevelPathAsACategoryAndItsParent() {
        CategoryPath path = CategoryPath.of("Beverages > Coffee");

        assertThat(path.name()).isEqualTo("Coffee");
        assertThat(path.parent()).isEqualTo("Beverages");
    }

    /**
     * Categories go two deep, so a long path has to lose something. The deepest
     * two are the most specific — an item among the polos belongs there, not on
     * a "Clothing" shelf holding half the shop.
     */
    @Test
    void keepsTheTwoDeepestLevelsOfALongPath() {
        CategoryPath path = CategoryPath.of(
                "Clothing, Shoes & Jewelry › Men › Clothing › Active › Active Shirts & Tees › Polos");

        assertThat(path.name()).isEqualTo("Polos");
        assertThat(path.parent()).isEqualTo("Active Shirts & Tees");
    }

    /**
     * A slash is not a hierarchy. Shops write "Health / Beauty" as the name of
     * one category, and splitting it would invent a parent they never had.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Health / Beauty", "Salt / Pepper", "Bits & Bobs"})
    void leavesNamesThatMerelyContainPunctuationAlone(String raw) {
        assertThat(CategoryPath.of(raw).name()).isEqualTo(raw);
        assertThat(CategoryPath.of(raw).parent()).isNull();
    }

    @Test
    void ignoresEmptyLevelsAndStrayArrows() {
        CategoryPath path = CategoryPath.of("  Beverages ›  › Coffee  ");

        assertThat(path.name()).isEqualTo("Coffee");
        assertThat(path.parent()).isEqualTo("Beverages");
    }

    @Test
    void hasNothingToSayAboutAnEmptyCell() {
        assertThat(CategoryPath.of(null).isEmpty()).isTrue();
        assertThat(CategoryPath.of("   ").isEmpty()).isTrue();
        assertThat(CategoryPath.of(" › ").isEmpty()).isTrue();
    }
}
