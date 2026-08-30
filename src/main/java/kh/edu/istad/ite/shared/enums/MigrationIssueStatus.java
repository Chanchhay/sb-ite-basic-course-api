package kh.edu.istad.ite.shared.enums;

/** Whether a finding still stands between the operator and a prepared import. */
public enum MigrationIssueStatus {

    OPEN,

    /** The operator has said what to do. The decision is on the issue. */
    RESOLVED,

    /** Looked at and deliberately left. Warnings may be dismissed; errors may not. */
    DISMISSED
}
