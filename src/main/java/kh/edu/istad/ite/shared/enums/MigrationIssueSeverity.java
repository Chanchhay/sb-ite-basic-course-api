package kh.edu.istad.ite.shared.enums;

/**
 * How much of an operator's attention one finding deserves.
 *
 * Six, because a migration produces findings of genuinely different kinds and
 * flattening them is what makes a review screen unreadable. Four thousand
 * prices tidied silently and twelve items with no name are not the same news,
 * and an operator who has to read past the first to reach the second will
 * eventually stop reading.
 */
public enum MigrationIssueSeverity {

    /** Already dealt with, deterministically. Shown so the operator can audit it. */
    AUTO_FIXED,

    /** Worth knowing. Nothing to do. */
    INFO,

    /** We have a proposal. Accepting it is one click; ignoring it is fine. */
    SUGGESTION,

    /** Imports as it stands, but somebody should look. */
    WARNING,

    /** Cannot proceed until a person decides. The whole point of the queue. */
    REVIEW_REQUIRED,

    /** The data is wrong and no decision fixes it. The file has to change. */
    ERROR
}
