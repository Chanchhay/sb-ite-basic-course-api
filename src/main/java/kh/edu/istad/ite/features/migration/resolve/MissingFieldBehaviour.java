package kh.edu.istad.ite.features.migration.resolve;

/**
 * What an absent value means for a given field.
 *
 * The distinction the missing-information screen is built on. A file with no
 * barcode column and a file with no unit column are both incomplete, and only
 * one of them is a problem — showing both as problems would bury the one that
 * matters under a list of things nobody needs to do anything about.
 */
public enum MissingFieldBehaviour {

    /**
     * Nothing can carry this field's absence. Somebody has to supply it.
     *
     * The value may still arrive from a joined file or a rule; what this says
     * is that if none of those reach it, the migration stops.
     */
    REQUIRED,

    /**
     * A FluxiBiz rule can settle it from what is already known.
     *
     * Only for rules the catalogue itself enforces. Anything merely usually
     * true is a default, and defaults get confirmed.
     */
    DERIVABLE,

    /**
     * One choice for the whole migration will do, once somebody makes it.
     *
     * Distinct from REQUIRED because of what the screen offers: a field
     * nobody can supply row by row but everybody can answer once.
     */
    DEFAULTABLE,

    /** Absence is an ordinary answer. Left empty, no question asked. */
    OPTIONAL
}
