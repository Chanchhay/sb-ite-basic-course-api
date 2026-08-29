package kh.edu.istad.ite.shared.enums;

/**
 * What a source file is for, within one migration.
 *
 * A customer rarely sends one tidy export. They send what their old system
 * would give them: a product list, a stock count taken that morning, a price
 * list somebody maintains in a spreadsheet. Each holds part of the answer and
 * none holds all of it, and knowing which is which is what lets the rest of
 * this feature join them rather than ask the operator to paste columns
 * together by hand.
 *
 * Named for what the file describes rather than for what will be done with it,
 * because the same stock export feeds an opening-stock import for a shop that
 * already has its items and a joined item import for a shop that does not.
 */
public enum MigrationSourcePurpose {

    /** The catalogue itself — names, codes, categories, units. */
    PRODUCTS,

    /** Quantities on hand, usually keyed by a code and nothing else. */
    STOCK,

    /** Prices kept apart from the catalogue, as they often are. */
    PRICES,

    /** A category list, where the shop keeps one separately. */
    CATEGORIES,

    /**
     * Not yet said.
     *
     * The state a file is in between being uploaded and an operator saying
     * what it is. Left explicit rather than defaulted to PRODUCTS, because a
     * stock file silently treated as a catalogue would produce items with no
     * names and a very confusing screen.
     */
    UNKNOWN
}
