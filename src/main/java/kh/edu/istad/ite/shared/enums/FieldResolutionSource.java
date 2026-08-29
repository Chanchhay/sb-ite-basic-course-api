package kh.edu.istad.ite.shared.enums;

/**
 * Where a prepared value came from, and therefore how much to trust it.
 *
 * Declared in order of preference: the earlier the answer is found, the less
 * anybody had to assume to get it. That ordering is the whole of the
 * missing-field strategy — a value read straight out of the customer's file
 * beats one joined from a second file, which beats one derived from a
 * FluxiBiz rule, which beats one somebody chose for the whole migration.
 *
 * Recorded rather than inferred, because the difference matters later. "Every
 * item is counted in pieces" is a fact when the file said so and an assumption
 * when an operator said so, and the day a shop asks why half their catalogue
 * is weighed in the wrong unit, that distinction is the answer.
 */
public enum FieldResolutionSource {

    /** The value was in the record's own source file. */
    DIRECT_SOURCE,

    /** Another source file in the same migration supplied it, through a join. */
    JOINED_SOURCE,

    /**
     * A FluxiBiz rule settles it.
     *
     * Only for rules the catalogue itself already enforces — a service does
     * not hold stock — never for anything that is merely usually true.
     */
    DERIVED,

    /** A rule an operator set for this migration filled it in. */
    MIGRATION_DEFAULT,

    /** An operator answered this particular question. */
    OPERATOR_RESOLUTION,

    /** Nothing could supply it. Blocking, if the field is required. */
    UNRESOLVED
}
