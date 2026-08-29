package kh.edu.istad.ite.features.migration.resolve;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.field.ImportFieldRequirement;
import kh.edu.istad.ite.shared.enums.ImportTargetType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a migration does about each field the source did not supply.
 *
 * Kept beside the importer's own {@link ImportFieldRequirement} rather than
 * folded into it, because the two are answering different questions. The
 * importer asks whether a shopkeeper's own file can be accepted, and it can
 * afford to fill gaps quietly — the shopkeeper wrote the file and knows what
 * they left out. A migration is reading a stranger's export on somebody's
 * behalf, and a gap filled quietly here is an assumption nobody ever agreed
 * to, discovered months later as a shelf of items counted in the wrong unit.
 *
 * So this is stricter on purpose, in exactly two places. Item Type and Track
 * Stock both have defaults in the importer; here they are asked about, once,
 * for the whole file. The answer is then written into the prepared workbook
 * explicitly, which means the importer never falls back on its own default and
 * the two can never disagree about what the file meant.
 */
public final class MigrationFieldPolicy {

    private MigrationFieldPolicy() {
    }

    /**
     * @param suggestion what to offer as the pre-filled answer, where there is
     *                   an obvious one. Offered, never applied unasked.
     */
    public record Policy(
            ImportField field,
            MissingFieldBehaviour behaviour,
            String suggestion,
            String question
    ) {

        public boolean blocksImport() {
            return behaviour == MissingFieldBehaviour.REQUIRED
                    || behaviour == MissingFieldBehaviour.DEFAULTABLE;
        }

        /**
         * Whether one answer for the whole migration is a legitimate answer.
         *
         * False for a name, and that is the entire distinction between
         * REQUIRED and DEFAULTABLE. Everything else here is a property of the
         * shop — how they count things, whether they track stock — which one
         * person can reasonably state once. What their products are called is
         * a property of each product, and no rule can know it.
         */
        public boolean answerableForEveryone() {
            return behaviour != MissingFieldBehaviour.REQUIRED;
        }
    }

    private static final Map<ImportField, Policy> ITEM_POLICIES = itemPolicies();

    private static Map<ImportField, Policy> itemPolicies() {
        Map<ImportField, Policy> policies = new LinkedHashMap<>();

        policies.put(ImportField.NAME, new Policy(
                ImportField.NAME,
                MissingFieldBehaviour.REQUIRED,
                null,
                "These rows have no item name. A name cannot be invented — remove them from the"
                        + " source, or add a file that names them."));

        policies.put(ImportField.ITEM_GROUP, new Policy(
                ImportField.ITEM_GROUP,
                MissingFieldBehaviour.DEFAULTABLE,
                null,
                "These rows say no category. Name one to file them all under."));

        policies.put(ImportField.UNIT, new Policy(
                ImportField.UNIT,
                MissingFieldBehaviour.DEFAULTABLE,
                null,
                "These rows say nothing about what the item is counted in. Choose a unit for"
                        + " them — it cannot be guessed from the name."));

        policies.put(ImportField.ITEM_TYPE, new Policy(
                ImportField.ITEM_TYPE,
                MissingFieldBehaviour.DEFAULTABLE,
                "Physical",
                "These rows do not say what kind of item this is."));

        policies.put(ImportField.TRACK_INVENTORY, new Policy(
                ImportField.TRACK_INVENTORY,
                MissingFieldBehaviour.DERIVABLE,
                "Yes",
                "These physical items do not say whether FluxiBiz should count their stock."));

        return policies;
    }

    /**
     * How this field behaves when nothing supplied it, for this kind of import.
     *
     * Falls back to the importer's own requirement for anything the migration
     * has no separate opinion about, so the two stay in step by default and
     * differ only where a difference was argued for.
     */
    public static Policy of(ImportField field, ImportTargetType targetType) {
        if (targetType == ImportTargetType.ITEM) {
            Policy policy = ITEM_POLICIES.get(field);

            if (policy != null) {
                return policy;
            }
        }

        ImportFieldRequirement requirement = field.requirementFor(targetType);

        MissingFieldBehaviour behaviour = switch (requirement == null
                ? ImportFieldRequirement.OPTIONAL
                : requirement) {
            case REQUIRED -> MissingFieldBehaviour.REQUIRED;
            case REQUIRED_OR_DEFAULTED -> MissingFieldBehaviour.DEFAULTABLE;
            case IDENTIFIER, OPTIONAL -> MissingFieldBehaviour.OPTIONAL;
        };

        return new Policy(field, behaviour, null, "These rows have no " + field.getLabel() + ".");
    }

    /**
     * The fields worth reporting on, in the order a person would ask about them.
     *
     * Optional fields are absent. A migration that told an operator four
     * thousand items have no parent category would be telling the truth and
     * wasting their attention, and attention spent on the harmless is
     * attention not spent on the unit nobody has chosen.
     */
    public static List<ImportField> fieldsThatMatter(ImportTargetType targetType) {
        return ImportField.forTarget(targetType).stream()
                .filter(field -> of(field, targetType).behaviour() != MissingFieldBehaviour.OPTIONAL)
                .toList();
    }
}
