package kh.edu.istad.ite.features.dataimport.canonical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import kh.edu.istad.ite.shared.enums.UnitCategory;

/**
 * A unit the uploaded workbook says it needs, read from its Units sheet.
 *
 * A shop migrating in counts things in words FluxiBiz has never heard of —
 * sacks, crates, trays — and until now the import could only refuse them and
 * send someone off to Item config to type each one in before starting again.
 * Declaring them beside the items lets one file describe everything it needs.
 *
 * What it deliberately does not do is guess. A file naming a unit it never
 * declares is still an error, because "sack" could be a weight or a count and
 * being wrong about that quietly corrupts every quantity it touches.
 *
 * @param name     what the unit is called — "Kilogram"
 * @param symbol   the short form a row is likely to use — "kg"
 * @param category what it measures, which nothing else can supply
 * @param note     the shop's own aside, kept as they wrote it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeclaredUnit(
        String name,
        String symbol,
        UnitCategory category,
        String note
) {

    /** Whether a row's unit cell is naming this one. */
    public boolean answersTo(String value) {
        return matches(name, value) || matches(symbol, value);
    }

    /** How this reads on screen: "Kilogram (kg)", or just the name. */
    public String label() {
        return symbol == null || symbol.isBlank() ? name : name + " (" + symbol + ")";
    }

    private static boolean matches(String mine, String theirs) {
        return mine != null
                && theirs != null
                && mine.trim().equalsIgnoreCase(theirs.trim());
    }
}
