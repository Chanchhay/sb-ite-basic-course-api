package kh.edu.istad.ite.shared.enums;

/**
 * What a migration is carrying across.
 *
 * Named rather than assumed, so the pipeline that profiles, maps, normalises
 * and resolves a file is not quietly an item pipeline. Customers, discounts
 * and historical orders will arrive the same way; they differ in which
 * importer receives the result, not in how a file is understood.
 *
 * Only the catalogue and its current stock are handled today. A value is added
 * when something actually migrates it, rather than standing as a promise.
 */
public enum MigrationEntityType {

    UNIT,
    ITEM_GROUP,
    ITEM,
    ITEM_OPTION,
    OPENING_STOCK
}
