package kh.edu.istad.ite.features.dataimport.commit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A swatch is a promise about what will arrive in the box.
 *
 * So this answers only for names it is sure of. A grey circle labelled "Rose
 * Gold" is a worse answer than no circle at all, and the import relies on the
 * empty answer to decide not to offer one.
 */
class ColourNamesTest {

    @Test
    void knowsTheColoursShopsActuallyType() {
        assertThat(ColourNames.hexFor("Black")).contains("#000000");
        assertThat(ColourNames.hexFor("Silver")).contains("#c0c0c0");
        assertThat(ColourNames.hexFor("Navy")).contains("#000080");
    }

    /** The two-word names retail uses constantly. */
    @Test
    void knowsCompoundRetailColours() {
        assertThat(ColourNames.hexFor("Rose Gold")).contains("#b76e79");
        assertThat(ColourNames.hexFor("Space Grey")).contains("#4a4a4a");
        assertThat(ColourNames.hexFor("Off White")).contains("#f5f2ea");
    }

    /** However a file happens to punctuate or capitalise it. */
    @Test
    void ignoresSpacingAndCase() {
        assertThat(ColourNames.hexFor("ROSE-GOLD")).contains("#b76e79");
        assertThat(ColourNames.hexFor("rose_gold")).contains("#b76e79");
        assertThat(ColourNames.hexFor("  Navy  ")).contains("#000080");
    }

    /** A finish is not a colour: Matte Black is still black. */
    @Test
    void seesPastAFinish() {
        assertThat(ColourNames.hexFor("Matte Black")).contains("#000000");
        assertThat(ColourNames.hexFor("Brushed Silver")).contains("#c0c0c0");
    }

    /**
     * A shade that is its own colour is matched as itself, never reduced to the
     * colour it is a shade of — Light Blue must not come back as Blue.
     */
    @Test
    void prefersTheWholeNameOverItsLastWord() {
        assertThat(ColourNames.hexFor("Light Blue")).contains("#add8e6");
        assertThat(ColourNames.hexFor("Blue")).contains("#1e6fd9");
    }

    /** Made-up names get no swatch, which is the point. */
    @Test
    void answersNothingForANameItCannotBeSureOf() {
        assertThat(ColourNames.hexFor("Sunset Fade")).isEmpty();
        assertThat(ColourNames.hexFor("Limited Edition")).isEmpty();
        assertThat(ColourNames.hexFor("Whole Milk")).isEmpty();
    }

    @Test
    void answersNothingForNothing() {
        assertThat(ColourNames.hexFor(null)).isEmpty();
        assertThat(ColourNames.hexFor("   ")).isEmpty();
    }
}
