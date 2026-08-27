package kh.edu.istad.ite.shared.enums;

/**
 * What to do with a row that matches something already in the catalogue.
 *
 * There is no third option that creates it anyway: a shop that imports its
 * price list twice should end up with one catalogue, not two.
 *
 * UPDATE_EXISTING is refused for opening stock. The inventory ledger is
 * append-only — an opening balance is the start of an item's history, and the
 * domain rejects a second one outright — so a re-import can only leave that
 * item alone. Opening stock rows therefore always skip.
 */
public enum ImportDuplicateStrategy {
    SKIP,
    UPDATE_EXISTING
}
