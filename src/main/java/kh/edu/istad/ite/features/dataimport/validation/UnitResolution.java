package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;

import java.util.List;
import java.util.UUID;

/**
 * What a Unit cell turned out to mean.
 *
 * Five answers, because a shop deserves a different sentence for each. Being
 * told "kg is not one of your units" when the file plainly declares Kilogram
 * on its Units sheet is the kind of message that makes people distrust the
 * whole screen.
 *
 * @param existingId the unit already in the catalogue, when there was one
 * @param declared   the unit this file will create, when it declared one
 * @param detail     what the message needs beyond the name — a conflicting
 *                   type, or the units an ambiguous cell matched
 */
public record UnitResolution(Outcome outcome, UUID existingId, DeclaredUnit declared, String detail) {

    public enum Outcome {

        /** Already in the catalogue. Use it, do not make another. */
        EXISTING,

        /** Not there, but the file declares it. The commit will create it. */
        WILL_BE_CREATED,

        /** Not there and not declared. Only the shop can say what it measures. */
        NOT_FOUND,

        /** Declared as something the catalogue already disagrees with. */
        TYPE_CONFLICT,

        /** Matches more than one unit, and picking one would be a guess. */
        AMBIGUOUS
    }

    public static UnitResolution existing(UUID id) {
        return new UnitResolution(Outcome.EXISTING, id, null, null);
    }

    public static UnitResolution willBeCreated(DeclaredUnit declared) {
        return new UnitResolution(Outcome.WILL_BE_CREATED, null, declared, null);
    }

    public static UnitResolution notFound() {
        return new UnitResolution(Outcome.NOT_FOUND, null, null, null);
    }

    public static UnitResolution typeConflict(DeclaredUnit declared, String existingCategory) {
        return new UnitResolution(Outcome.TYPE_CONFLICT, null, declared, existingCategory);
    }

    public static UnitResolution ambiguous(List<String> names) {
        return new UnitResolution(Outcome.AMBIGUOUS, null, null, String.join("\" and \"", names));
    }

    /** Whether the row can go ahead on the strength of this. */
    public boolean isUsable() {
        return outcome == Outcome.EXISTING || outcome == Outcome.WILL_BE_CREATED;
    }
}
