package kh.edu.istad.ite.features.migration.service;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.shared.enums.MigrationSourcePurpose;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a file is probably for, judged only by what it has columns for.
 *
 * A first guess, shown so the operator can correct it in one click rather than
 * choose from a list every time. Getting it wrong costs nothing important: the
 * purpose steers which joins get suggested and how the file is described on
 * screen, and never what any value means.
 *
 * The reasoning is deliberately crude. A file with quantities and no names is
 * a stock count; a file with names is a catalogue. Anything cleverer would be
 * guessing at intent from column headings, and a confidently mislabelled file
 * is more confusing to an operator than an honestly unlabelled one.
 */
final class SourcePurposeGuess {

    private SourcePurposeGuess() {
    }

    private static final Set<ImportField> NAMING = Set.of(ImportField.NAME, ImportField.DESCRIPTION);

    static MigrationSourcePurpose from(List<String> headings) {
        Set<ImportField> fields = headings.stream()
                .map(heading -> suggest(heading))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        boolean names = fields.stream().anyMatch(NAMING::contains);

        if (names) {
            return MigrationSourcePurpose.PRODUCTS;
        }

        if (fields.contains(ImportField.OPENING_STOCK)) {
            return MigrationSourcePurpose.STOCK;
        }

        if (fields.contains(ImportField.PRICE) || fields.contains(ImportField.COST_PRICE)) {
            return MigrationSourcePurpose.PRICES;
        }

        if (fields.contains(ImportField.ITEM_GROUP) || fields.contains(ImportField.PARENT_GROUP)) {
            return MigrationSourcePurpose.CATEGORIES;
        }

        return MigrationSourcePurpose.UNKNOWN;
    }

    /**
     * The importer's own alias table, asked about one heading.
     *
     * Reused rather than restated so a heading a shop's own import recognises
     * is a heading a migration recognises. Two lists would drift, and the one
     * that drifted would be this one.
     */
    private static Optional<ImportField> suggest(String heading) {
        return ImportField.suggestFor(heading, kh.edu.istad.ite.shared.enums.ImportTargetType.ITEM);
    }
}
