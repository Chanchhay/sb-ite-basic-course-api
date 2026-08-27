package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.features.dataimport.field.ImportField;

import java.util.Map;
import java.util.Optional;

/**
 * The hex a colour name is worth showing as, when we can be confident of it.
 *
 * A swatch is a promise: the circle a shopper clicks is meant to be the colour
 * of the thing they will receive. A grey circle labelled "Rose Gold" is worse
 * than no circle at all, so this answers only for names it actually knows and
 * leaves the rest to be handled as plain option names.
 *
 * Deliberately not exhaustive and deliberately not clever. It covers the words
 * shops actually type into a colour column, and a shop selling "Sunset Fade"
 * gets an honest option list rather than a guessed-at swatch.
 */
public final class ColourNames {

    private ColourNames() {
    }

    /**
     * Finishes and shades that sit in front of a colour without changing which
     * colour it is. Stripped only as a second attempt, so "Light Blue" is
     * matched as itself before "Blue" is ever considered.
     */
    private static final String[] MODIFIERS = {
            "matte", "matt", "gloss", "glossy", "satin", "metallic", "brushed",
            "classic", "solid", "plain", "pure", "true",
    };

    private static final Map<String, String> HEX_BY_NAME = Map.ofEntries(
            Map.entry("black", "#000000"),
            Map.entry("white", "#ffffff"),
            Map.entry("offwhite", "#f5f2ea"),
            Map.entry("cream", "#fffdd0"),
            Map.entry("ivory", "#fffff0"),
            Map.entry("beige", "#f5f5dc"),
            Map.entry("grey", "#808080"),
            Map.entry("gray", "#808080"),
            Map.entry("charcoal", "#36454f"),
            Map.entry("silver", "#c0c0c0"),
            Map.entry("gold", "#d4af37"),
            Map.entry("rosegold", "#b76e79"),
            Map.entry("bronze", "#cd7f32"),
            Map.entry("copper", "#b87333"),
            Map.entry("red", "#e02020"),
            Map.entry("burgundy", "#800020"),
            Map.entry("maroon", "#800000"),
            Map.entry("pink", "#ffc0cb"),
            Map.entry("hotpink", "#ff69b4"),
            Map.entry("orange", "#f57c00"),
            Map.entry("peach", "#ffdab9"),
            Map.entry("yellow", "#f5c400"),
            Map.entry("mustard", "#ffdb58"),
            Map.entry("green", "#1e9e4a"),
            Map.entry("olive", "#808000"),
            Map.entry("mint", "#98ff98"),
            Map.entry("forestgreen", "#228b22"),
            Map.entry("blue", "#1e6fd9"),
            Map.entry("navy", "#000080"),
            Map.entry("navyblue", "#000080"),
            Map.entry("skyblue", "#87ceeb"),
            Map.entry("lightblue", "#add8e6"),
            Map.entry("midnightblue", "#191970"),
            Map.entry("teal", "#008080"),
            Map.entry("turquoise", "#40e0d0"),
            Map.entry("purple", "#7b3fa0"),
            Map.entry("violet", "#8f00ff"),
            Map.entry("lavender", "#e6e6fa"),
            Map.entry("indigo", "#4b0082"),
            Map.entry("brown", "#8b5a2b"),
            Map.entry("tan", "#d2b48c"),
            Map.entry("khaki", "#c3b091"),
            Map.entry("coral", "#ff7f50"),
            Map.entry("salmon", "#fa8072"),
            Map.entry("spacegrey", "#4a4a4a"),
            Map.entry("spacegray", "#4a4a4a")
    );

    /** The colour's hex, or empty when the name is not one we can be sure of. */
    public static Optional<String> hexFor(String colourName) {
        String normalized = ImportField.normalize(colourName);

        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        String direct = HEX_BY_NAME.get(normalized);

        if (direct != null) {
            return Optional.of(direct);
        }

        for (String modifier : MODIFIERS) {
            if (normalized.startsWith(modifier) && normalized.length() > modifier.length()) {
                String remainder = normalized.substring(modifier.length());
                String hex = HEX_BY_NAME.get(remainder);

                if (hex != null) {
                    return Optional.of(hex);
                }
            }
        }

        return Optional.empty();
    }
}
