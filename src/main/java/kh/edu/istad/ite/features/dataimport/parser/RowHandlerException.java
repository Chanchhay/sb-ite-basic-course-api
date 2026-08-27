package kh.edu.istad.ite.features.dataimport.parser;

/**
 * Carries a failure raised by whatever was handed the rows, back out through
 * the reader that was handing them over.
 *
 * A reader wraps what goes wrong inside it in "this file could not be read",
 * which is the right thing to tell a shop about a corrupt workbook and quite
 * the wrong thing to tell them about a database that rejected the row. Without
 * this the second is disguised as the first, and the real cause never reaches
 * a log — which is exactly how a stuck import becomes impossible to diagnose.
 */
public class RowHandlerException extends RuntimeException {

    public RowHandlerException(RuntimeException cause) {
        super(cause);
    }

    /** The original failure, to be rethrown once clear of the reader. */
    @Override
    public synchronized RuntimeException getCause() {
        return (RuntimeException) super.getCause();
    }
}
