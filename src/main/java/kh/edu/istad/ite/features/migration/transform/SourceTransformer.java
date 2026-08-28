package kh.edu.istad.ite.features.migration.transform;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.migration.normalize.DataNormalizationService;
import kh.edu.istad.ite.features.migration.normalize.Normalized;
import kh.edu.istad.ite.features.migration.normalize.UnitAliases;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.UnitCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a whole source file through the operator's mapping and decisions.
 *
 * The one place that turns somebody else's export into FluxiBiz's own terms —
 * and it stops there. What comes out is rows in the shape of our official
 * workbook, which the ordinary importer then checks, previews and commits like
 * any other upload. Nothing here touches the catalogue.
 *
 * Deterministic on purpose. Running it twice over the same file with the same
 * mapping and the same decisions produces the same rows and the same findings,
 * which is what lets an operator fix one column and re-run without losing the
 * forty answers they already gave.
 */
@Component
@RequiredArgsConstructor
public class SourceTransformer {

    private final DataNormalizationService normalizer;

    /**
     * @param mapping    source heading to FluxiBiz field, as the operator settled it
     * @param decisions  what the operator already answered, keyed by
     *                   {@code field + "|" + source value} — the same key an
     *                   issue is grouped by, so a decision found here is a
     *                   question not asked again
     */
    public TransformResult transform(
            List<SourceRow> rows,
            Map<String, ImportField> mapping,
            ImportTargetType targetType,
            Map<String, DeclaredUnit> decisions
    ) {
        return transform(rows, mapping, targetType, decisions, Map.of());
    }

    /**
     * @param axisNames what to call the option axes, when the operator has said.
     *                  Otherwise the source column's own heading is used, which
     *                  is where a file keeps that word.
     */
    public TransformResult transform(
            List<SourceRow> rows,
            Map<String, ImportField> mapping,
            ImportTargetType targetType,
            Map<String, DeclaredUnit> decisions,
            Map<String, String> axisNames
    ) {
        List<PreparedRow> prepared = new ArrayList<>();
        List<TransformResult.Finding> findings = new ArrayList<>();
        Map<String, DeclaredUnit> units = new LinkedHashMap<>();

        for (SourceRow row : rows) {
            PreparedRow out = PreparedRow.empty(row.rowNumber());

            for (Map.Entry<String, ImportField> column : mapping.entrySet()) {
                String raw = row.value(column.getKey());
                ImportField field = column.getValue();

                if (raw == null || raw.isBlank()) {
                    continue;
                }

                if (field == ImportField.UNIT) {
                    readUnit(raw, row.rowNumber(), out, findings, units, decisions);
                    continue;
                }

                Normalized read = normalizer.normalize(field, raw);

                if (read.isUnreadable()) {
                    findings.add(new TransformResult.Finding(
                            "VALUE_UNREADABLE",
                            field.name(),
                            raw.trim(),
                            read.problem() + " for " + field.getLabel() + ".",
                            row.rowNumber(),
                            true));
                    continue;
                }

                if (read.wasChanged()) {
                    findings.add(new TransformResult.Finding(
                            "VALUE_NORMALIZED",
                            field.name(),
                            raw.trim(),
                            read.rule() + ": \"" + raw.trim() + "\" reads as \"" + read.value() + "\".",
                            row.rowNumber(),
                            false));
                }

                out.put(field, read.value());
            }

            nameTheOptionAxes(out, mapping, axisNames);
            requireName(out, row.rowNumber(), findings);

            if (!out.isEmpty()) {
                prepared.add(out);
            }
        }

        requireOneUnitPerItem(prepared, findings);

        return new TransformResult(prepared, findings, List.copyOf(units.values()));
    }

    /**
     * A unit is either obvious, already decided, or a question.
     *
     * Never a guess. "KG" is a kilogram anywhere; "SACK" is a count in most
     * shops and a mass in some, and a unit read wrongly does not announce
     * itself — it silently changes what every quantity counted in it means. So
     * the known ones are resolved, the operator's earlier answers are reused,
     * and anything left becomes one decision covering every row that used it.
     */
    private void readUnit(
            String raw,
            int rowNumber,
            PreparedRow out,
            List<TransformResult.Finding> findings,
            Map<String, DeclaredUnit> units,
            Map<String, DeclaredUnit> decisions
    ) {
        String value = raw.trim();
        String key = ImportField.UNIT.name() + "|" + value.toLowerCase();

        DeclaredUnit decided = decisions.get(key);

        if (decided != null) {
            units.putIfAbsent(decided.name().toLowerCase(), decided);
            out.put(ImportField.UNIT, symbolOf(decided));
            return;
        }

        DeclaredUnit known = UnitAliases.resolve(value).orElse(null);

        if (known != null) {
            units.putIfAbsent(known.name().toLowerCase(), known);
            out.put(ImportField.UNIT, symbolOf(known));

            if (!symbolOf(known).equalsIgnoreCase(value)) {
                findings.add(new TransformResult.Finding(
                        "UNIT_NORMALIZED",
                        ImportField.UNIT.name(),
                        value,
                        "\"" + value + "\" reads as " + known.name() + " (" + symbolOf(known) + ").",
                        rowNumber,
                        false));
            }
            return;
        }

        findings.add(new TransformResult.Finding(
                "UNIT_UNKNOWN",
                ImportField.UNIT.name(),
                value,
                "\"" + value + "\" is not a unit we recognise. Say what it is called and whether"
                        + " it counts, weighs or measures.",
                rowNumber,
                true));
    }

    /** An item with no name is not an item, and no decision can supply one. */
    private void requireName(PreparedRow out, int rowNumber, List<TransformResult.Finding> findings) {
        if (out.isEmpty() || out.get(ImportField.NAME) != null) {
            return;
        }

        findings.add(new TransformResult.Finding(
                "NAME_MISSING",
                ImportField.NAME.name(),
                "",
                "This row has no item name.",
                rowNumber,
                true));
    }


    /**
     * Gives each option axis its name.
     *
     * A source file says what an axis is in the heading and what this row's
     * value is in the cell — a SIZE column holding "Small". FluxiBiz wants both,
     * and shows the name to shoppers, so a heading of "SZ" is worth an operator
     * overriding.
     */
    private void nameTheOptionAxes(
            PreparedRow out,
            Map<String, ImportField> mapping,
            Map<String, String> axisNames
    ) {
        nameAxis(out, mapping, axisNames, ImportField.OPTION_1_VALUE, ImportField.OPTION_1_NAME, "option1");
        nameAxis(out, mapping, axisNames, ImportField.OPTION_2_VALUE, ImportField.OPTION_2_NAME, "option2");
    }

    private void nameAxis(
            PreparedRow out,
            Map<String, ImportField> mapping,
            Map<String, String> axisNames,
            ImportField valueField,
            ImportField nameField,
            String key
    ) {
        if (out.get(valueField) == null || out.get(nameField) != null) {
            return;
        }

        String chosen = axisNames.get(key);

        if (chosen != null && !chosen.isBlank()) {
            out.put(nameField, chosen.trim());
            return;
        }

        mapping.entrySet().stream()
                .filter(entry -> entry.getValue() == valueField)
                .map(Map.Entry::getKey)
                .findFirst()
                .ifPresent(heading -> out.put(nameField, readable(heading)));
    }

    /** "SUB_CAT" is a column; "Sub Cat" is something to show a shopper. */
    private String readable(String heading) {
        String spaced = heading.replaceAll("[_\\-]+", " ").trim();

        if (spaced.isEmpty()) {
            return heading;
        }

        StringBuilder out = new StringBuilder();

        for (String word : spaced.split("\\s+")) {
            out.append(Character.toUpperCase(word.charAt(0)))
               .append(word.length() == 1 ? "" : word.substring(1).toLowerCase())
               .append(' ');
        }

        return out.toString().trim();
    }

    /**
     * Every row of one item has to agree about its unit.
     *
     * FluxiBiz keeps the unit on the item rather than the shelf, so a file
     * selling Small by the piece and Large by the kilogram is describing
     * something the catalogue cannot hold. Caught here rather than left to the
     * importer, because here we can say which rows disagreed.
     */
    private void requireOneUnitPerItem(
            List<PreparedRow> rows,
            List<TransformResult.Finding> findings
    ) {
        Map<String, String> unitByGroup = new LinkedHashMap<>();

        for (PreparedRow row : rows) {
            String group = row.get(ImportField.OPTION_GROUP_KEY);
            String unit = row.get(ImportField.UNIT);

            if (group == null || unit == null) {
                continue;
            }

            String claimed = unitByGroup.putIfAbsent(group, unit);

            if (claimed != null && !claimed.equalsIgnoreCase(unit)) {
                findings.add(new TransformResult.Finding(
                        "UNIT_CONFLICT_IN_GROUP",
                        ImportField.UNIT.name(),
                        group,
                        "The rows of \"" + group + "\" are counted in both \"" + claimed
                                + "\" and \"" + unit + "\". Every option of an item shares one unit.",
                        row.sourceRowNumber(),
                        true));
            }
        }
    }

    private String symbolOf(DeclaredUnit unit) {
        return unit.symbol() == null || unit.symbol().isBlank() ? unit.name() : unit.symbol();
    }

    /** The measures an operator may pick from when deciding an unknown unit. */
    public List<UnitCategory> unitCategories() {
        return UnitAliases.categories();
    }
}
