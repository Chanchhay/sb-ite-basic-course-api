package kh.edu.istad.ite.shared.enums;

/**
 * Which question a migration is answering about the shop's old system.
 *
 * Three, because they are genuinely different problems and conflating them is
 * how migrations go wrong. What the shop looks like today is a photograph;
 * what happened before is a film; what changes while both systems run is a
 * live feed. Only the photograph is taken here.
 */
public enum MigrationMode {

    /** What should FluxiBiz look like at the moment of the move? */
    STATE_MIGRATION,

    /** What happened before that moment? Not implemented yet. */
    HISTORY_MIGRATION,

    /** What changes after it, while the old system still runs? Not implemented yet. */
    LIVE_SYNC
}
