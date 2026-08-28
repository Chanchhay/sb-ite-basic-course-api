package kh.edu.istad.ite.shared.enums;

/**
 * Where one migration attempt has got to.
 *
 * The happy path runs UPLOADED → MAPPED → VALIDATING → READY → COMMITTING →
 * COMMITTED. Nothing in production is touched before COMMITTING, which is the
 * whole point of the staging step: a shop can look at what its old system
 * really contained, and change its mind, without having paid for it yet.
 *
 * The two failure states are deliberately different. VALIDATION_FAILED means
 * the file was read and found wanting — the user fixes the file or the column
 * matching and tries again. FAILED means the import itself broke: the file
 * could not be read, or the commit stopped part-way. Only FAILED can leave
 * production data partly written, and the report says exactly which rows made
 * it in.
 */
public enum ImportStatus {
    UPLOADED,
    MAPPED,
    VALIDATING,
    READY,
    VALIDATION_FAILED,
    COMMITTING,
    COMMITTED,

    /** Being taken back out again, row by row. */
    REVERTING,

    /**
     * Taken back out. Not necessarily to nothing: an item sold since the
     * import has to stay, so the rows say what actually went.
     */
    REVERTED,
    FAILED
}
