package kh.edu.istad.ite.features.migration.resolve;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.migration.transform.PreparedRow;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import kh.edu.istad.ite.shared.enums.FieldResolutionSource;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills what the customer's files left out, in order of how much it assumes.
 *
 * Runs after the sources have been joined and read, because until then
 * "missing" is not a fact — a product with no unit in the product list may
 * well have one in the price list, and asking an operator about it first would
 * be asking them to answer a question the data already answers.
 *
 * What it will not do is complete a record to make it importable. A row with
 * no name stays blocked however many rules are set, because there is no rule
 * that can know what the customer calls their product, and an item imported
 * under a name we made up is worse than an item that failed to import.
 */
@Component
public class MissingFieldResolutionService {

    /** How many real examples to carry back for an operator to judge by. */
    private static final int SAMPLES = 5;

    /**
     * The one rule the catalogue itself enforces, so it needs nobody's consent.
     *
     * A service has no shelf and a download never runs out. FluxiBiz refuses
     * stock against either, so deriving "does not track stock" is not an
     * assumption about the shop's business — it is a restatement of what the
     * catalogue will do anyway.
     */
    private static final String STOCK_RULE = "Services and digital items do not hold stock";

    /**
     * @param rules what an operator has already decided, most specific first
     * @return the findings still needing a person, plus what every field
     *         resolved to
     */
    public Outcome resolve(
            List<PreparedRow> rows,
            ImportTargetType targetType,
            List<FieldRule> rules
    ) {
        for (PreparedRow row : rows) {
            derive(row);
            applyRules(row, targetType, rules);
        }

        return report(rows, targetType);
    }

    /** @param findings what still has to be decided before this can be imported */
    public record Outcome(MissingFieldReport report, List<TransformResult.Finding> findings) {
    }

    /**
     * What FluxiBiz can settle on its own.
     *
     * Deliberately one rule. Every extra derivation is a place the migration
     * decides something on the shop's behalf, and the bar for adding one is
     * that the catalogue would enforce it regardless — not that it is usually
     * right.
     */
    private void derive(PreparedRow row) {
        if (row.has(ImportField.TRACK_INVENTORY)) {
            return;
        }

        String itemType = row.get(ImportField.ITEM_TYPE);

        if (itemType == null) {
            return;
        }

        if (itemType.equalsIgnoreCase("Service") || itemType.equalsIgnoreCase("Digital")) {
            row.put(ImportField.TRACK_INVENTORY, "No", FieldValue.derived("No", STOCK_RULE));
        }
    }

    /**
     * The operator's decisions, narrowest first.
     *
     * A rule for one category beats a rule for everything, so an operator can
     * say "services are counted in services, and everything else in pieces"
     * as two rules rather than as one rule and a list of exceptions.
     *
     * Narrowest is worked out here rather than trusted from the caller. The
     * list arrives in whatever order the operator answered the questions, and
     * a migration that came out differently depending on that order would be
     * the same catalogue imported two ways.
     */
    private void applyRules(PreparedRow row, ImportTargetType targetType, List<FieldRule> rules) {
        List<FieldRule> narrowestFirst = rules.stream()
                .sorted(Comparator.comparingInt(MissingFieldResolutionService::breadthOf))
                .toList();

        /*
         * Passes until nothing more is settled. A rule scoped to an item type
         * can only be judged once that item type is known, and the rule that
         * settles it may sit anywhere in the list — so a single pass would
         * apply or drop it depending on the order the answers came in.
         *
         * Each pass only fills fields that are still empty, so there are at
         * most as many passes as there are fields.
         */
        boolean settledSomething = true;

        while (settledSomething) {
            settledSomething = false;

            String category = row.get(ImportField.ITEM_GROUP);
            String itemType = row.get(ImportField.ITEM_TYPE);

            for (FieldRule rule : narrowestFirst) {
                if (rule.value() == null || rule.value().isBlank()) {
                    continue;
                }

                if (row.has(rule.field()) || !rule.covers(category, itemType)) {
                    continue;
                }

                /*
                 * A rule cannot supply what only the customer knows. Choosing one
                 * value for every nameless row would import a shelf of identical
                 * items nobody can tell apart, and the shop would have to find and
                 * delete them by hand — worse than the rows simply not importing.
                 */
                if (!MigrationFieldPolicy.of(rule.field(), targetType).answerableForEveryone()) {
                    continue;
                }

                row.put(rule.field(), rule.value(),
                        FieldValue.decided(rule.value(), rule.rule()));

                settledSomething = true;

                /*
                 * An item type settled by a rule can settle the stock question
                 * behind it, in the same pass. Otherwise an operator who answers
                 * "these are all services" would be asked immediately afterwards
                 * whether services hold stock, which FluxiBiz already knows.
                 */
                if (rule.field() == ImportField.ITEM_TYPE) {
                    itemType = rule.value();
                    derive(row);
                }

                if (rule.field() == ImportField.ITEM_GROUP) {
                    category = rule.value();
                }
            }
        }
    }

    /**
     * How much of the catalogue a rule claims — lower reaches fewer rows.
     *
     * A category names a shelf the operator picked out by hand; an item type
     * cuts across the whole catalogue; everything reaches the rest. Rules of
     * equal breadth keep the order they were given, so two answers that truly
     * overlap settle the way the operator wrote them.
     */
    private static int breadthOf(FieldRule rule) {
        return switch (rule.scope()) {
            case CATEGORY -> 0;
            case ITEM_TYPE -> 1;
            case ALL -> 2;
        };
    }

    /**
     * Counts what is left, field by field.
     *
     * Only fields where absence means something are counted. Reporting that
     * four thousand items have no parent category would be true and useless,
     * and it would bury the one field nobody has chosen a value for.
     */
    private Outcome report(List<PreparedRow> rows, ImportTargetType targetType) {
        List<MissingFieldReport.FieldStatus> statuses = new ArrayList<>();
        List<TransformResult.Finding> findings = new ArrayList<>();
        Map<FieldResolutionSource, Integer> overall = new LinkedHashMap<>();

        for (PreparedRow row : rows) {
            row.provenance().values()
                    .forEach(value -> overall.merge(value.resolution(), 1, Integer::sum));
        }

        for (ImportField field : MigrationFieldPolicy.fieldsThatMatter(targetType)) {
            MigrationFieldPolicy.Policy policy = MigrationFieldPolicy.of(field, targetType);

            List<PreparedRow> without = rows.stream().filter(row -> !row.has(field)).toList();
            Map<FieldResolutionSource, Integer> byKind = new LinkedHashMap<>();
            Set<String> samples = new LinkedHashSet<>();

            for (PreparedRow row : rows) {
                FieldValue origin = row.originOf(field);

                if (origin != null) {
                    byKind.merge(origin.resolution(), 1, Integer::sum);
                }
            }

            for (PreparedRow row : without) {
                if (samples.size() >= SAMPLES) {
                    break;
                }

                String name = row.get(ImportField.NAME);
                samples.add(name == null ? "Row " + row.sourceRowNumber() : name);
            }

            boolean blocking = policy.blocksImport()
                    || (policy.behaviour() == MissingFieldBehaviour.DERIVABLE && !without.isEmpty());

            statuses.add(new MissingFieldReport.FieldStatus(
                    field,
                    field.getLabel(),
                    policy.behaviour(),
                    rows.size() - without.size(),
                    without.size(),
                    blocking,
                    policy.suggestion(),
                    policy.question(),
                    List.copyOf(samples),
                    byKind));

            if (!without.isEmpty()) {
                overall.merge(FieldResolutionSource.UNRESOLVED, without.size(), Integer::sum);
            }

            if (blocking && !without.isEmpty()) {
                findings.addAll(findingsFor(field, policy, without));
            }
        }

        return new Outcome(new MissingFieldReport(statuses, overall), findings);
    }

    /**
     * One question, raised once per row so the count comes out right.
     *
     * The rows are grouped downstream by field and cause into a single issue
     * carrying the number affected, which is the form an operator can act on:
     * one decision, and it says how much rides on it. Emitting a single
     * finding here would group to an affected count of one and understate
     * every question in the file.
     *
     * A missing name is the exception that proves the shape. It is grouped the
     * same way and can never be answered, so it is filed as an error rather
     * than a decision — nothing an operator could choose would make those rows
     * importable.
     */
    private List<TransformResult.Finding> findingsFor(
            ImportField field,
            MigrationFieldPolicy.Policy policy,
            List<PreparedRow> without
    ) {
        boolean unanswerable = field == ImportField.NAME;
        String code = unanswerable ? "NAME_MISSING" : "FIELD_MISSING";

        return without.stream()
                .map(row -> new TransformResult.Finding(
                        code,
                        field.name(),
                        "",
                        policy.question(),
                        row.sourceRowNumber(),
                        true))
                .toList();
    }
}
