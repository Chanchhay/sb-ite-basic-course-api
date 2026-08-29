package kh.edu.istad.ite.shared.enums;

/**
 * What a source column's values look like, judged from the values alone.
 *
 * A guess about shape, never about meaning. Knowing a column holds decimals
 * does not make it a price — it might be a weight, a tax rate or a discount —
 * so this narrows what a column could be mapped to and never decides it.
 */
public enum SourceValueType {

    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATETIME,
    URL,

    /** Nothing to go on: every value empty, or no two alike in kind. */
    UNKNOWN
}
