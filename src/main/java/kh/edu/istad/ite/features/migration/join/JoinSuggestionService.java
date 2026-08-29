package kh.edu.istad.ite.features.migration.join;

import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.features.migration.entity.AssistedMigrationSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Works out which columns of two files are talking about the same thing.
 *
 * The honest version of a hard problem. There is no way to know that
 * {@code products.product_code} and {@code stock.prd_cd} are the same
 * identifier, so this measures the three things that are actually knowable —
 * whether the headings resemble each other, whether the values are unique
 * enough to identify anything, and how much the two sets of values overlap —
 * and shows all three to somebody who can decide.
 *
 * Names are refused outright as join keys. Two shops can both sell "Water",
 * one export spells it "WATER 500ML" and another "Water 500 ml", and a join on
 * names is wrong in both directions at once: it misses real matches and makes
 * false ones. The guide asks for names to require explicit confirmation; not
 * offering them at all is the same protection without the temptation.
 */
@Component
@RequiredArgsConstructor
public class JoinSuggestionService {

    /** Below this much overlap the pair is coincidence, not a relationship. */
    private static final double MIN_OVERLAP = 0.20;

    /** Below this the values repeat too much to identify a single record. */
    private static final double MIN_UNIQUENESS = 0.50;

    private static final int MAX_SUGGESTIONS = 12;

    /** Headings that name a thing rather than identify it. Never join keys. */
    private static final Set<String> NAME_LIKE = Set.of(
            "name", "itemname", "productname", "title", "description", "itemdescription",
            "details", "longdescription", "category", "categoryname", "department", "brand",
            "supplier", "notes", "note", "remark", "remarks", "comment");

    /** Fragments that mark a heading as an identifier rather than a value. */
    private static final List<String> IDENTIFIER_HINTS = List.of(
            "code", "sku", "barcode", "ean", "upc", "gtin", "ref", "id", "no", "number", "article");

    private final JoinAnalysisService analysis;

    /**
     * Every plausible way the main file could be joined to each of the others.
     *
     * Only pairs anchored on the main source are offered. A chain — prices
     * joined to stock joined to products — is a shape nobody has sent yet, and
     * offering it would mean explaining join order to an operator for no gain.
     */
    public List<JoinSuggestion> suggest(
            AssistedMigrationSource primary,
            List<AssistedMigrationSource> others,
            Map<UUID, List<SourceRow>> rowsBySource
    ) {
        List<SourceRow> leftRows = rowsBySource.getOrDefault(primary.getId(), List.of());

        if (leftRows.isEmpty()) {
            return List.of();
        }

        Map<String, Set<String>> leftKeys = keysOf(leftRows, primary.getSourceColumns());
        List<JoinSuggestion> suggestions = new ArrayList<>();

        for (AssistedMigrationSource right : others) {
            List<SourceRow> rightRows = rowsBySource.getOrDefault(right.getId(), List.of());

            if (rightRows.isEmpty()) {
                continue;
            }

            Map<String, Set<String>> rightKeys = keysOf(rightRows, right.getSourceColumns());

            for (Map.Entry<String, Set<String>> leftColumn : leftKeys.entrySet()) {
                for (Map.Entry<String, Set<String>> rightColumn : rightKeys.entrySet()) {
                    scoreOf(primary, leftRows, leftColumn, right, rightRows, rightColumn)
                            .ifPresent(suggestions::add);
                }
            }
        }

        suggestions.sort(Comparator.comparingDouble(JoinSuggestion::confidence).reversed());

        return suggestions.stream().limit(MAX_SUGGESTIONS).toList();
    }

    private Optional<JoinSuggestion> scoreOf(
            AssistedMigrationSource left,
            List<SourceRow> leftRows,
            Map.Entry<String, Set<String>> leftColumn,
            AssistedMigrationSource right,
            List<SourceRow> rightRows,
            Map.Entry<String, Set<String>> rightColumn
    ) {
        if (isNameLike(leftColumn.getKey()) || isNameLike(rightColumn.getKey())) {
            return Optional.empty();
        }

        Set<String> leftValues = leftColumn.getValue();
        Set<String> rightValues = rightColumn.getValue();

        if (leftValues.isEmpty() || rightValues.isEmpty()) {
            return Optional.empty();
        }

        /*
         * Uniqueness against the row count, not against the other column. A key
         * repeating across half the main file identifies a group rather than a
         * record, and joining on it would attach one product's stock to several.
         */
        double leftUniqueness = (double) leftValues.size() / leftRows.size();

        if (leftUniqueness < MIN_UNIQUENESS) {
            return Optional.empty();
        }

        long shared = leftValues.stream().filter(rightValues::contains).count();
        double overlap = (double) shared / Math.min(leftValues.size(), rightValues.size());

        if (overlap < MIN_OVERLAP) {
            return Optional.empty();
        }

        double headings = headingSimilarity(leftColumn.getKey(), rightColumn.getKey());

        /*
         * Overlap carries the most weight because it is the only one of the
         * three measured against both files at once. Two columns named the
         * same thing that share no values are not a relationship, however
         * encouraging the headings look.
         */
        double confidence =
                Math.min(0.99, (0.55 * overlap) + (0.20 * leftUniqueness) + (0.25 * headings));

        JoinQuality quality = analysis.analyse(
                leftRows, leftColumn.getKey(), rightRows, rightColumn.getKey());

        return Optional.of(new JoinSuggestion(
                left.getId(), left.getFileName(), leftColumn.getKey(),
                right.getId(), right.getFileName(), rightColumn.getKey(),
                confidence,
                reasonFor(overlap, leftUniqueness, headings),
                quality));
    }

    private String reasonFor(double overlap, double uniqueness, double headings) {
        StringBuilder reason = new StringBuilder()
                .append(percent(overlap))
                .append(" of the values appear in both");

        if (uniqueness > 0.95) {
            reason.append(", and they are unique in the main file");
        }

        if (headings >= 1.0) {
            reason.append(". The columns are named the same");
        }

        return reason.append('.').toString();
    }

    private String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    /**
     * How alike two headings are, without pretending to understand them.
     *
     * Exact after flattening, or one plainly contained in the other, or both
     * merely looking like identifiers. Anything subtler — edit distance,
     * stemming — would start matching "supplier_code" to "product_code", which
     * is precisely the confident wrong answer this feature exists to avoid.
     */
    private double headingSimilarity(String left, String right) {
        String a = flatten(left);
        String b = flatten(right);

        if (a.equals(b)) {
            return 1.0;
        }

        if (a.contains(b) || b.contains(a)) {
            return 0.6;
        }

        return looksLikeIdentifier(a) && looksLikeIdentifier(b) ? 0.3 : 0.0;
    }

    private boolean looksLikeIdentifier(String flattened) {
        return IDENTIFIER_HINTS.stream().anyMatch(flattened::contains);
    }

    private boolean isNameLike(String heading) {
        return NAME_LIKE.contains(flatten(heading));
    }

    private String flatten(String heading) {
        return heading == null
                ? ""
                : heading.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Each column's distinct values, folded the way a join would compare them. */
    private Map<String, Set<String>> keysOf(List<SourceRow> rows, List<String> columns) {
        Map<String, Set<String>> keys = new LinkedHashMap<>();

        columns.forEach(column -> keys.put(column, new LinkedHashSet<>()));

        for (SourceRow row : rows) {
            for (String column : columns) {
                String key = JoinKeys.of(row.value(column));

                if (key != null) {
                    keys.get(column).add(key);
                }
            }
        }

        return keys;
    }
}
