package kh.edu.istad.ite.shared.enums;

/**
 * How much of a source survives being joined to another.
 *
 * Only the two that are safe to offer. A left join keeps every record of the
 * main file whether or not the other one mentions it — the usual case, where a
 * stock export happens to be missing a few lines. An inner join keeps only
 * what both agree on, which is right when the second file defines the scope.
 *
 * Outer and cross joins are absent deliberately: both can produce more records
 * than the customer has products, and a migration that quietly invents rows is
 * worse than one that refuses.
 */
public enum MigrationJoinType {

    /** Every record of the left source, enriched where the right one matches. */
    LEFT,

    /** Only records both sources have. */
    INNER
}
