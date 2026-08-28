package kh.edu.istad.ite.shared.enums;

/**
 * How much weight a note on a row carries.
 *
 * Three rather than two because "the category Groceries will be created" is
 * not a warning about anything — it is the import saying what it is going to
 * do. Showing it in the same colour as a real problem taught shopkeepers to
 * read past the colour, which is the opposite of what it is for.
 */
public enum ImportIssueSeverity {

    /** What the import will do. Nothing to fix, nothing to weigh up. */
    INFO,

    /** Worth a second look, but the row still imports. */
    WARNING,

    /** The row cannot be imported until this is dealt with. */
    ERROR
}
