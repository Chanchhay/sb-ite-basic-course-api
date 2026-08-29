package kh.edu.istad.ite.features.migration.transform;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;
import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.migration.normalize.DataNormalizationService;
import kh.edu.istad.ite.features.migration.normalize.Normalized;
import kh.edu.istad.ite.features.migration.normalize.UnitAliases;
import kh.edu.istad.ite.features.migration.resolve.FieldValue;
import kh.edu.istad.ite.features.migration.resolve.ResolvedRecord;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.UnitCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads joined records through the operator's decisions into FluxiBiz's terms.
 *
 * The one place that turns somebody else's export into our own words — and it
 * stops there. What comes out is rows in the shape of our official workbook,
 * which the ordinary importer then checks, previews and commits like any other
 * upload. Nothing here touches the catalogue.
 *
 * Deterministic on purpose. Running it twice over the same records with the
 * same decisions produces the same rows and the same findings, which is what
 * lets an operator fix one column and re-run without losing the forty answers
 * they already gave — and what lets a single row be explained later by simply
 * running it again.
 */
@Component
@RequiredArgsConstructor
public class SourceTransformer {

    private final DataNormalizationService normalizer;

    /**
     * @param decisions what the operator already answered, keyed by
     *                  {@code field + "|" + source value} — the same key an
     *                  issue is grouped by, so a decision found here is a
     *                  question not asked again
     * @param axisNames what to call the option axes, when the operator has
     *                  said. Otherwise the source column's own heading is
     *                  used, which is where a file keeps that word.
     */
    public TransformResult transform(
            List<ResolvedRecord> records,
            ImportTargetType targetType,
            Map<String, DeclaredUnit> decisions,
            Map<String, String> axisNames
    ) {
        List<PreparedRow> prepared = new ArrayList<>();
        List<TransformResult.Finding> findings = new ArrayList<>();
        Map<String, DeclaredUnit> units = new LinkedHashMap<>();

        for (ResolvedRecord record : records) {
            PreparedRow out = PreparedRow.empty(record.rowNumber());

            for (Map.Entry<ImportField, FieldValue> entry : record.fields().entrySet()) {
                ImportField field = entry.getKey();
                FieldValue origin = entry.getValue();
                String raw = origin.value();

                if (raw == null || raw.isBlank()) {
                    continue;
                }

                if (field == ImportField.UNIT) {
                    readUnit(raw, record.rowNumber(), out, findings, units, decisions, origin);
                    continue;
                }

                Normalized read = normalizer.normalize(field, raw);

                if (read.isUnreadable()) {
                    findings.add(new TransformResult.Finding(
                            "VALUE_UNREADABLE",
                            field.name(),
                            raw.trim(),
                            read.problem() + " for " + field.getLabel() + ".",
                            record.rowNumber(),
                            true));
                    continue;
                }

                if (read.wasChanged()) {
                    findings.add(new TransformResult.Finding(
                            "VALUE_NORMALIZED",
                            field.name(),
                            raw.trim(),
                            read.rule() + ": \"" + raw.trim() + "\" reads as \"" + read.value() + "\".",
                            record.rowNumber(),
                            false));
                }

                out.put(field, read.value(), origin);
            }

            nameTheOptionAxes(out, axisNames);

            if (!out.isEmpty()) {
                prepared.add(out);
            }
        }

        requireOneUnitPerItem(prepared, findings);

        return new TransformResult(prepared, findings, List.copyOf(units.values()));
    }

    /**
     * The same reading, starting from one file's rows and one mapping.
     *
     * A single-source migration is a joined one with nothing to join, so it
     * takes the same path rather than a shorter one kept beside it — two paths
     * would eventually disagree about a file that used only one.
     */
    public TransformResult transform(
            List<SourceRow> rows,
            Map<String, ImportField> mapping,
            ImportTargetType targetType,
            Map<String, DeclaredUnit> decisions,
            Map<String, String> axisNames
    ) {
        return transform(asRecords(rows, mapping), targetType, decisions, axisNames);
    }

    public TransformResult transform(
            List<SourceRow> rows,
            Map<String, ImportField> mapping,
            ImportTargetType targetType,
            Map<String, DeclaredUnit> decisions
    ) {
        return transform(rows, mapping, targetType, decisions, Map.of());
    }

    /** One file's rows, read through its mapping and owing nothing to a join. */
    public static List<ResolvedRecord> asRecords(
            List<SourceRow> rows,
            Map<String, ImportField> mapping
    ) {
        List<ResolvedRecord> records = new ArrayList<>();

        for (SourceRow row : rows) {
            ResolvedRecord record = ResolvedRecord.empty(row.rowNumber());

            mapping.forEach((heading, field) -> {
                String value = row.value(heading);

                if (value != null && !value.isBlank()) {
                    record.offer(field, FieldValue.direct(value, null, heading, row.rowNumber()));
                }
            });

            records.add(record);
        }

        return records;
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
            Map<String, DeclaredUnit> decisions,
            FieldValue origin
    ) {
        String value = raw.trim();
        String key = ImportField.UNIT.name() + "|" + value.toLowerCase();

        DeclaredUnit decided = decisions.get(key);

        if (decided != null) {
            units.putIfAbsent(decided.name().toLowerCase(), decided);
            out.put(ImportField.UNIT, symbolOf(decided), origin);
            return;
        }

        DeclaredUnit known = UnitAliases.resolve(value).orElse(null);

        if (known != null) {
            units.putIfAbsent(known.name().toLowerCase(), known);
            out.put(ImportField.UNIT, symbolOf(known), origin);

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

    /**
     * Gives each option axis its name.
     *
     * A source file says what an axis is in the heading and what this row's
     * value is in the cell — a SIZE column holding "Small". FluxiBiz wants
     * both, and shows the name to shoppers, so a heading of "SZ" is worth an
     * operator overriding.
     */
    private void nameTheOptionAxes(PreparedRow out, Map<String, String> axisNames) {
        nameAxis(out, axisNames, ImportField.OPTION_1_VALUE, ImportField.OPTION_1_NAME, "option1");
        nameAxis(out, axisNames, ImportField.OPTION_2_VALUE, ImportField.OPTION_2_NAME, "option2");
    }

    private void nameAxis(
            PreparedRow out,
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
            out.put(nameField, chosen.trim(), FieldValue.decided(chosen.trim(), "Named by the operator"));
            return;
        }

        FieldValue origin = out.originOf(valueField);

        if (origin != null && origin.sourceColumn() != null) {
            out.put(nameField, readable(origin.sourceColumn()), origin);
        }
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
