package kh.edu.istad.ite.features.migration.normalize;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.shared.enums.UnitCategory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The units the world writes down, and what FluxiBiz calls them.
 *
 * Only the ones whose meaning is beyond argument. A kilogram is a mass
 * wherever it appears, so reading "KG", "Kg" and "kilograms" as one unit costs
 * nothing and saves an operator three identical decisions.
 *
 * Everything else is left alone on purpose. "SACK" is a count in most shops
 * and a mass in some; "TRAY" could be either; and a unit read wrongly does not
 * announce itself — it quietly makes every quantity counted in it mean
 * something else. Those become one decision each, put to a person.
 */
public final class UnitAliases {

    private UnitAliases() {
    }

    private static final Map<String, DeclaredUnit> KNOWN = Map.ofEntries(
            mass("kilogram", "Kilogram", "kg"), mass("kilograms", "Kilogram", "kg"),
            mass("kilo", "Kilogram", "kg"), mass("kgs", "Kilogram", "kg"), mass("kg", "Kilogram", "kg"),
            mass("gram", "Gram", "g"), mass("grams", "Gram", "g"), mass("gm", "Gram", "g"),
            mass("g", "Gram", "g"),
            volume("liter", "Liter", "L"), volume("litre", "Liter", "L"),
            volume("liters", "Liter", "L"), volume("litres", "Liter", "L"), volume("l", "Liter", "L"),
            volume("milliliter", "Milliliter", "ml"), volume("millilitre", "Milliliter", "ml"),
            volume("ml", "Milliliter", "ml"),
            count("piece", "Piece", "pc"), count("pieces", "Piece", "pc"),
            count("pcs", "Piece", "pc"), count("pc", "Piece", "pc"),
            count("each", "Piece", "pc"), count("ea", "Piece", "pc"), count("unit", "Piece", "pc"),
            count("can", "Can", "can"), count("cans", "Can", "can"),
            count("bottle", "Bottle", "btl"), count("bottles", "Bottle", "btl"),
            count("box", "Box", "box"), count("boxes", "Box", "box"),
            count("carton", "Carton", "ctn"), count("cartons", "Carton", "ctn"),
            count("ctn", "Carton", "ctn"),
            count("bag", "Bag", "bag"), count("bags", "Bag", "bag"),
            count("pack", "Pack", "pack"), count("packs", "Pack", "pack"),
            count("cup", "Cup", "cup"), count("cups", "Cup", "cup"),
            count("dozen", "Dozen", "dz"), count("set", "Set", "set"),
            count("service", "Service", "svc"), count("session", "Session", "session"),
            count("license", "License", "license"), count("licence", "License", "license")
    );

    /**
     * The unit this value plainly means, if it plainly means one.
     *
     * An empty answer is the useful one: it is what sends "SACK" to an
     * operator instead of into the catalogue as a guess.
     */
    public static Optional<DeclaredUnit> resolve(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String key = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");

        return Optional.ofNullable(KNOWN.get(key));
    }

    /** The three FluxiBiz measures, for an operator deciding an unknown one. */
    public static List<UnitCategory> categories() {
        return List.of(UnitCategory.COUNT, UnitCategory.MASS, UnitCategory.VOLUME);
    }

    private static Map.Entry<String, DeclaredUnit> mass(String alias, String name, String symbol) {
        return Map.entry(alias, new DeclaredUnit(name, symbol, UnitCategory.MASS, null));
    }

    private static Map.Entry<String, DeclaredUnit> volume(String alias, String name, String symbol) {
        return Map.entry(alias, new DeclaredUnit(name, symbol, UnitCategory.VOLUME, null));
    }

    private static Map.Entry<String, DeclaredUnit> count(String alias, String name, String symbol) {
        return Map.entry(alias, new DeclaredUnit(name, symbol, UnitCategory.COUNT, null));
    }
}
