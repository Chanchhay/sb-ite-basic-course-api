package kh.edu.istad.ite.features.dataimport.canonical;

import kh.edu.istad.ite.features.dataimport.field.ImportField;

import java.util.ArrayList;
import java.util.List;

/**
 * The option one row describes, read out of a variant export.
 *
 * FluxiBiz sells an option as a size and a colour together: {@code optionName}
 * carries the size half, {@code colourValue} names one of the item's declared
 * colours, and {@code label} is the readable pair a receipt prints — "Small /
 * Black".
 *
 * Which of a file's option columns is the colour is decided by what the file
 * calls the axis, not by where it sits. A shop selling by Size and Colour gets
 * a real colour with a swatch; one selling by Size and Material gets "Small /
 * Cotton" as the option's name and no colour at all, which is the honest
 * answer — inventing a colour called Cotton would put a swatch on the
 * storefront and make the catalogue refuse the item for not coming in it.
 *
 * @param optionName  every non-colour axis, joined — "Small", or "Small / Cotton".
 *                    Never empty on a row that names any option at all: on an
 *                    item sold only by colour, the colour is the option.
 * @param colourValue the colour axis, or null when the file has none
 */
public record RowOptions(String optionName, String colourValue) {

    /** A row that names no options at all: a plain item, sold as one thing. */
    public static final RowOptions NONE = new RowOptions(null, null);

    /**
     * Reads up to two option axes into FluxiBiz's shape.
     *
     * An axis with no value is skipped: a file listing Option 2 as Color and
     * leaving it blank on a row means that row has no colour, not a colour
     * called nothing.
     */
    public static RowOptions of(String name1, String value1, String name2, String value2) {
        List<String> plain = new ArrayList<>();
        String colour = null;

        if (value1 != null) {
            if (ImportField.isColourAxis(name1)) {
                colour = value1;
            } else {
                plain.add(value1);
            }
        }

        if (value2 != null) {
            if (ImportField.isColourAxis(name2) && colour == null) {
                colour = value2;
            } else {
                plain.add(value2);
            }
        }

        /*
         * A colour is always tied to an option — the catalogue has no notion of
         * one floating on its own, and the item screen never writes one: it
         * sets the option name on every row it saves and keeps the shelf
         * against the pair. So an item sold only by colour has the colour as
         * its option too, which is exactly what the screen shows when it reads
         * such an item back.
         */
        String optionName = plain.isEmpty() ? colour : String.join(" / ", plain);

        return new RowOptions(optionName, colour);
    }

    public boolean isPresent() {
        return optionName != null || colourValue != null;
    }

    /**
     * Whether the colour is an axis of its own rather than the option itself.
     *
     * A shirt in Small and Black varies along two axes, and a colour picker
     * beside the size picker says something the size picker cannot. A watch
     * sold only in Rose Gold varies along one: the colour *is* the option, and
     * showing it twice — once as an option and again as a swatch — tells the
     * shopper nothing they did not already read.
     */
    public boolean hasDistinctColourAxis() {
        return optionName != null && colourValue != null && !optionName.equals(colourValue);
    }

    /**
     * What the option is called on a receipt, a ticket or a stock report.
     *
     * The catalogue insists every option has one, and uses it to tell two
     * options of the same item apart — so it has to carry both halves.
     */
    public String label() {
        // On an item sold only by colour the two halves are the same thing, and
        // "Silver / Silver" is not what belongs on a receipt.
        if (optionName != null && colourValue != null && !optionName.equals(colourValue)) {
            return optionName + " / " + colourValue;
        }

        return optionName != null ? optionName : colourValue;
    }
}
