package kh.edu.istad.ite.shared.enums;

/**
 * Where an assisted migration has got to.
 *
 * Deliberately more states than the shop's own import has. A shopkeeper's file
 * already fits FluxiBiz, so there is nothing to work out; a customer's old POS
 * export has to be read, understood, argued with and agreed before it can even
 * become an import — and an operator who steps away needs to come back to a
 * job that says which of those it is waiting on.
 */
public enum AssistedMigrationStatus {

    /** Created, with no file yet. */
    CREATED,

    /** The customer's file is stored, untouched. */
    UPLOADED,

    /** Being read and profiled. */
    ANALYZING,

    /** Read. Columns profiled, mapping suggested. */
    ANALYZED,

    /** Suggestions were not enough — an operator has to say what a column means. */
    MAPPING_REQUIRED,

    /** Turning the source into FluxiBiz's own terms. */
    TRANSFORMING,

    /** Transformed, but something needs a person to decide. */
    REVIEW_REQUIRED,

    /** Nothing left to decide. Ready to become an import. */
    READY,

    /** Handing over to the ordinary importer. */
    PREPARING_IMPORT,

    /**
     * An import job exists and holds this data.
     *
     * The end of assisted migration's job. What happens next — checking,
     * review, commit — belongs to the importer every shop already uses.
     */
    IMPORT_PREPARED,

    /** The prepared import was brought in. */
    COMPLETED,

    FAILED
}
