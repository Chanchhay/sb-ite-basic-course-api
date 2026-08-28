package kh.edu.istad.ite.features.dataimport.canonical;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A category cell, which may name one shelf or the whole aisle leading to it.
 *
 * Exports disagree about this. A till writes "Coffee"; a webshop writes
 * "Clothing, Shoes & Jewelry › Men › Clothing › Active › Polos", the entire
 * path from the department to the shelf. Both mean the same thing — put the
 * item here — so both are read into the same pair.
 *
 * @param parent the category the item's category sits under, or null
 * @param name   the category the item is filed in
 */
public record CategoryPath(String parent, String name) {

    public static final CategoryPath EMPTY = new CategoryPath(null, null);

    /**
     * Only the arrows, never the slash.
     *
     * "›" and ">" mean a hierarchy and nothing else. A slash does not: shops
     * write "Health / Beauty" and "Salt / Pepper" as the names of single
     * categories, and splitting those would invent a parent the shop never had
     * and file half their catalogue under it.
     */
    private static final Pattern SEPARATOR = Pattern.compile("\\s*[\u203a>]\\s*");

    /**
     * The two deepest levels of a path, which is as much as FluxiBiz can hold.
     *
     * Categories go two deep, so a five-level path has to lose something. The
     * deepest two are kept because they are the most specific: an item on
     * "… › Active Shirts & Tees › Polos" belongs among the polos, and filing
     * it under "Clothing" instead would put it on a shelf with half the shop.
     */
    public static CategoryPath of(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        List<String> levels = new ArrayList<>();

        for (String level : SEPARATOR.split(raw)) {
            String trimmed = level.trim();

            if (!trimmed.isEmpty()) {
                levels.add(trimmed);
            }
        }

        if (levels.isEmpty()) {
            return EMPTY;
        }
        if (levels.size() == 1) {
            return new CategoryPath(null, levels.getFirst());
        }

        return new CategoryPath(levels.get(levels.size() - 2), levels.getLast());
    }

    public boolean isEmpty() {
        return name == null;
    }
}
