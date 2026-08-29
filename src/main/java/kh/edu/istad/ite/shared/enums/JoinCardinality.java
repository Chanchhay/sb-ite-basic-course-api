package kh.edu.istad.ite.shared.enums;

/**
 * What a join key actually turned out to be, once both sides were counted.
 *
 * Worth measuring rather than assuming. An operator picking "product_code"
 * believes it identifies a product; if the stock file lists one line per
 * warehouse, it does not, and the join they just approved would multiply their
 * catalogue. This is how that gets said out loud before it happens.
 */
public enum JoinCardinality {

    /** Each key appears at most once on both sides. What everyone expects. */
    ONE_TO_ONE,

    /** Unique on the left, repeated on the right — several stock lines per item. */
    ONE_TO_MANY,

    /** Repeated on the left, unique on the right — several rows share one price. */
    MANY_TO_ONE,

    /**
     * Repeated on both sides.
     *
     * Refused. There is no honest way to decide which left row a given right
     * row belongs to, and joining anyway would multiply rows silently.
     */
    MANY_TO_MANY
}
