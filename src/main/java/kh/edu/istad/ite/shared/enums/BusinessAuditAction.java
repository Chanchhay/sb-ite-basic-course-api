package kh.edu.istad.ite.shared.enums;

/**
 * What a shop's own audit log records.
 *
 * Deliberately separate from {@link AdminActionType}, which is FluxiBiz staff
 * acting *on* businesses. These are the shop's own people acting inside it, and
 * the two are read by different audiences on different screens.
 */
public enum BusinessAuditAction {

    /** Someone signed in and used the application. */
    STAFF_SIGNED_IN,

    STAFF_CREATED,
    STAFF_UPDATED,
    STAFF_SUSPENDED,
    STAFF_REACTIVATED,
    STAFF_DELETED,

    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED
}

// No ROLE_ASSIGNED: roles are handed out through the staff form, which already
// records STAFF_CREATED and STAFF_UPDATED. A filter option that can never match
// anything is worse than one that is not offered.
