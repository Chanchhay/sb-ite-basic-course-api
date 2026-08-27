package kh.edu.istad.ite.features.dataimport.field;

/**
 * What kind of value a field expects.
 *
 * Read by the matching screen to say what a column should contain, and by the
 * canonical mapper to decide how to read the text out of a cell.
 */
public enum ImportFieldType {
    TEXT,
    NUMBER,
    MONEY,
    BOOLEAN,
    ENUM
}
