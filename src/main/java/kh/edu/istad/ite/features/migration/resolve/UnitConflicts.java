package kh.edu.istad.ite.features.migration.resolve;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.shared.enums.UnitCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Units this migration would declare that the shop already disagrees with.
 *
 * The shop has a "Box" and counts them. The migration reads a file where box
 * is a weight, and somebody chooses MASS. Both are defensible and they cannot
 * both be true of one unit, and the one that loses is whichever the importer
 * happens to resolve second — silently, with every quantity in boxes then
 * meaning something other than what the file said.
 *
 * The importer already refuses this, and refusing it there is too late to be
 * useful: the operator has handed over, the shop has an import job full of
 * errors, and the decision that caused it is three screens back. Caught here
 * it is one sentence naming both sides while the person who chose is still
 * looking at the choice.
 */
public final class UnitConflicts {

    private UnitConflicts() {
    }

    /**
     * One unit the shop already has.
     *
     * A record rather than the catalogue's own entity so this stays a
     * function of two lists — the thing that decides whether it can be tested
     * without a database, and so whether it is tested at all.
     */
    public record ExistingUnit(String name, UnitCategory category) {
    }

    /**
     * @param declared what the prepared workbook would ask the importer to create
     * @param existing the shop's own units, by whatever name they gave them
     */
    public static List<TransformResult.Finding> find(
            List<DeclaredUnit> declared,
            List<ExistingUnit> existing
    ) {
        /*
         * Every unit of that name, not the first. A shop can have its own
         * "Box" alongside the platform's, and if either already measures what
         * the migration means then nothing is being redefined — reporting a
         * conflict against the other one would block a migration that is
         * perfectly consistent with the catalogue it is going into.
         */
        Map<String, List<ExistingUnit>> known = new LinkedHashMap<>();

        existing.forEach(unit -> {
            if (unit.name() != null) {
                known.computeIfAbsent(key(unit.name()), ignored -> new ArrayList<>()).add(unit);
            }
        });

        List<TransformResult.Finding> findings = new ArrayList<>();

        for (DeclaredUnit unit : declared) {
            if (unit.name() == null || unit.category() == null) {
                continue;
            }

            List<ExistingUnit> theirs = known.getOrDefault(key(unit.name()), List.of());

            if (theirs.isEmpty()
                    || theirs.stream().anyMatch(one -> one.category() == unit.category())) {
                continue;
            }

            findings.add(new TransformResult.Finding(
                    "UNIT_TYPE_CONFLICT",
                    "UNIT",
                    unit.name(),
                    "This shop already has a unit called \"" + unit.name() + "\" that "
                            + measures(theirs.getFirst().category())
                            + ", and this migration would make it "
                            + measures(unit.category()) + ". One of the two is wrong, and the"
                            + " catalogue cannot hold both.",
                    0,
                    true));
        }

        return findings;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** What a measure does, in the words an operator would use. */
    private static String measures(UnitCategory category) {
        return switch (category) {
            case COUNT -> "counts";
            case MASS -> "weighs";
            case VOLUME -> "measures volume";
        };
    }
}
