package kh.edu.istad.ite.features.dataimport.field;

/** How badly one import needs a column matched to a given field. */
public enum ImportFieldRequirement {

    /** Nothing can be imported without it. */
    REQUIRED,

    /**
     * Needed, but a single choice for the whole file will do instead.
     *
     * Most exports have no column for the unit an item is counted in, so the
     * matching screen lets one be picked once rather than refusing the file.
     */
    REQUIRED_OR_DEFAULTED,

    /**
     * One of the fields marked this way has to be matched, and any one of them
     * is enough — the ways of saying which existing item a row is about.
     */
    IDENTIFIER,

    OPTIONAL
}
