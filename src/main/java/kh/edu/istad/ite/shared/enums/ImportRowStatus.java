package kh.edu.istad.ite.shared.enums;

/**
 * What happened to one row of the uploaded file.
 *
 * PENDING until it has been checked. DUPLICATE is not an error — it is a row
 * that matches something the shop already has, and what becomes of it is
 * decided by the import's duplicate strategy, so it is counted and shown
 * apart from INVALID rather than lumped in with it.
 */
public enum ImportRowStatus {
    PENDING,
    VALID,
    DUPLICATE,
    INVALID,
    CREATED,
    UPDATED,
    SKIPPED,
    FAILED
}
