package kh.edu.istad.ite.features.dataimport.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * How a file's option columns become FluxiBiz's size-and-colour pair.
 *
 * The rule that matters: which axis is the colour is decided by what the file
 * calls it, never by where it sits. A shop selling by Size and Material has no
 * colour, and inventing one would put a swatch on the storefront and make the
 * catalogue refuse the item for not coming in it.
 */
class RowOptionsTest {

    @Test
    void readsSizeAndColourIntoTheirOwnHalves() {
        RowOptions options = RowOptions.of("Size", "Small", "Color", "Black");

        assertThat(options.optionName()).isEqualTo("Small");
        assertThat(options.colourValue()).isEqualTo("Black");
        assertThat(options.label()).isEqualTo("Small / Black");
    }

    @Test
    void acceptsEitherSpellingOfColour() {
        assertThat(RowOptions.of("Size", "S", "Colour", "Navy").colourValue()).isEqualTo("Navy");
        assertThat(RowOptions.of("Colour", "Navy", null, null).colourValue()).isEqualTo("Navy");
    }

    /**
     * A colour is always tied to an option. The catalogue keeps the shelf
     * against the pair and the item screen sets an option name on every row it
     * saves, so an item sold only by colour has the colour as its option too —
     * and prints as "Silver", not "Silver / Silver".
     */
    @Test
    void tiesAColourOnlyItemToAnOptionOfItsOwn() {
        RowOptions options = RowOptions.of("Color", "Silver", null, null);

        assertThat(options.optionName()).isEqualTo("Silver");
        assertThat(options.colourValue()).isEqualTo("Silver");
        assertThat(options.label()).isEqualTo("Silver");
    }

    /** Material is not a colour, so it joins the option's name instead. */
    @Test
    void foldsANonColourSecondAxisIntoTheOptionName() {
        RowOptions options = RowOptions.of("Size", "Small", "Material", "Cotton");

        assertThat(options.optionName()).isEqualTo("Small / Cotton");
        assertThat(options.colourValue()).isNull();
        assertThat(options.label()).isEqualTo("Small / Cotton");
    }

    /** A colour listed first is still the colour. */
    @Test
    void findsTheColourWhicheverAxisItIsOn() {
        RowOptions options = RowOptions.of("Color", "Black", "Size", "Small");

        assertThat(options.colourValue()).isEqualTo("Black");
        assertThat(options.optionName()).isEqualTo("Small");
    }

    @Test
    void handlesAnItemSoldBySizeAlone() {
        RowOptions options = RowOptions.of("Size", "Large", null, null);

        assertThat(options.optionName()).isEqualTo("Large");
        assertThat(options.colourValue()).isNull();
        assertThat(options.label()).isEqualTo("Large");
    }

    /** A blank axis means this row has no such option, not an option called nothing. */
    @Test
    void ignoresAnAxisWithNoValue() {
        RowOptions options = RowOptions.of("Size", "Large", "Color", null);

        assertThat(options.colourValue()).isNull();
        assertThat(options.label()).isEqualTo("Large");
    }

    @Test
    void knowsWhenARowNamesNoOptionsAtAll() {
        assertThat(RowOptions.of(null, null, null, null).isPresent()).isFalse();
        assertThat(RowOptions.NONE.isPresent()).isFalse();
        assertThat(RowOptions.of("Size", "Small", null, null).isPresent()).isTrue();
    }

    /**
     * A colour is only an axis of its own when something else varies too. A
     * watch sold in three colours has one axis — showing a swatch beside an
     * identical option name says nothing twice.
     */
    @Test
    void knowsWhenTheColourIsAnAxisOfItsOwn() {
        assertThat(RowOptions.of("Size", "Small", "Color", "Black").hasDistinctColourAxis())
                .isTrue();
        assertThat(RowOptions.of("Color", "Rose Gold", null, null).hasDistinctColourAxis())
                .isFalse();
        assertThat(RowOptions.of("Size", "Small", "Material", "Cotton").hasDistinctColourAxis())
                .isFalse();
    }
}
