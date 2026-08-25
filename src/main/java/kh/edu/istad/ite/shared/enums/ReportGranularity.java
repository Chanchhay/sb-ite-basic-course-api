package kh.edu.istad.ite.shared.enums;

/**
 * How finely a report slices the range it covers.
 *
 * The value is what Postgres {@code date_trunc} is given, so the grouping is
 * the database's own calendar arithmetic rather than something reimplemented
 * here — weeks start where Postgres says they start, and months and years are
 * right across the boundaries that catch hand-rolled versions out.
 */
public enum ReportGranularity {

    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    private final String truncField;

    ReportGranularity(String truncField) {
        this.truncField = truncField;
    }

    /** Bound as a parameter, never concatenated into the query text. */
    public String truncField() {
        return truncField;
    }
}
