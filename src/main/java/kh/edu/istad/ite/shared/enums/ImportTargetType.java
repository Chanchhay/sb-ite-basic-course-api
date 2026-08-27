package kh.edu.istad.ite.shared.enums;

/**
 * What a migration is bringing in.
 *
 * ITEM is the broad one: a shop's export usually carries the category and the
 * quantity on hand in the same row as the item, so an ITEM import may also
 * create the item groups it names and post the opening stock it counts.
 * The other two exist for shops whose old system exported them separately.
 */
public enum ImportTargetType {
    ITEM_GROUP,
    ITEM,
    OPENING_STOCK
}
