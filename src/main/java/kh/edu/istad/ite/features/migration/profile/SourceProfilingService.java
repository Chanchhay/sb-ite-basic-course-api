package kh.edu.istad.ite.features.migration.profile;

import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.shared.enums.SourceValueType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a file and says what is in each column, without deciding what it means.
 *
 * This is the step that makes a strange export approachable. An operator
 * looking at a column called {@code prd_desc} learns nothing; the same column
 * described as ten thousand rows, all filled, nearly all different, holding
 * "Coca Cola 330ml" and "Sourdough Loaf", is obviously the name.
 *
 * Counts and samples are gathered in one pass and kept. Distinct counts are
 * held per column in a set, which is the one thing here that grows with the
 * file — capped, because knowing a column has "more than a thousand" different
 * values tells us everything the mapping needs.
 */
@Component
public class SourceProfilingService {

    /** Enough to tell an identifier from a category; past this the answer is "many". */
    private static final int MAX_DISTINCT_TRACKED = 1_000;

    /** Shown to a person, so a handful of real ones beats a statistic. */
    private static final int MAX_SAMPLES = 5;

    private static final Pattern INTEGER = Pattern.compile("^-?\\d{1,15}$");
    private static final Pattern DECIMAL =
            Pattern.compile("^-?[\\p{Sc}]?\\s*\\d{1,3}(,\\d{3})*(\\.\\d+)?\\s*[A-Za-z]{0,3}$|^-?\\.?\\d+(\\.\\d+)?$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$|^\\d{1,2}/\\d{1,2}/\\d{2,4}$");
    private static final Pattern DATETIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}.*$");
    private static final Set<String> BOOLEANS = Set.of(
            "true", "false", "yes", "no", "y", "n", "1", "0",
            "active", "inactive", "enabled", "disabled");

    /** Everything learned about one file. */
    public record SourceProfile(int rows, List<ColumnProfile> columns) {
    }

    public SourceProfile profile(List<String> columns, List<SourceRow> rows) {
        Map<String, Set<String>> distinct = new LinkedHashMap<>();
        Map<String, List<String>> samples = new LinkedHashMap<>();
        Map<String, Integer> filled = new LinkedHashMap<>();
        Map<String, Map<SourceValueType, Integer>> shapes = new LinkedHashMap<>();

        for (String column : columns) {
            distinct.put(column, new LinkedHashSet<>());
            samples.put(column, new ArrayList<>());
            filled.put(column, 0);
            shapes.put(column, new LinkedHashMap<>());
        }

        for (SourceRow row : rows) {
            for (String column : columns) {
                String value = row.value(column);

                if (value == null || value.isBlank()) {
                    continue;
                }

                String trimmed = value.trim();

                filled.merge(column, 1, Integer::sum);

                Set<String> seen = distinct.get(column);
                if (seen.size() < MAX_DISTINCT_TRACKED) {
                    seen.add(trimmed.toLowerCase());
                }

                List<String> shown = samples.get(column);
                if (shown.size() < MAX_SAMPLES && !shown.contains(trimmed)) {
                    shown.add(trimmed);
                }

                shapes.get(column).merge(shapeOf(trimmed), 1, Integer::sum);
            }
        }

        List<ColumnProfile> profiles = new ArrayList<>();

        for (String column : columns) {
            profiles.add(new ColumnProfile(
                    column,
                    rows.size(),
                    filled.get(column),
                    distinct.get(column).size(),
                    List.copyOf(samples.get(column)),
                    settleType(shapes.get(column), filled.get(column))
            ));
        }

        return new SourceProfile(rows.size(), profiles);
    }

    /**
     * The column's type, if its values agree well enough.
     *
     * A column is only called a number when nearly all of it reads as one. Real
     * exports carry a stray "N/A" or a note in the price column, and refusing
     * to call that column decimal over one bad cell would leave the operator
     * mapping everything by hand — while calling a mostly-text column decimal
     * because a few rows look numeric would be worse.
     */
    private SourceValueType settleType(Map<SourceValueType, Integer> shapes, int filled) {
        if (filled == 0) {
            return SourceValueType.UNKNOWN;
        }

        SourceValueType leader = null;
        int best = 0;

        for (Map.Entry<SourceValueType, Integer> shape : shapes.entrySet()) {
            if (shape.getValue() > best) {
                leader = shape.getKey();
                best = shape.getValue();
            }
        }

        if (leader == null || (double) best / filled < 0.9) {
            return SourceValueType.TEXT;
        }

        /*
         * Whole numbers are decimals that happen to have no fraction. A stock
         * column of 40, 24, 15 is integer; a price column of 4.50 and 3 is
         * decimal with some tidy values, and calling it integer would be wrong
         * about the column rather than about the row.
         */
        if (leader == SourceValueType.INTEGER
                && shapes.getOrDefault(SourceValueType.DECIMAL, 0) > 0) {
            return SourceValueType.DECIMAL;
        }

        return leader;
    }

    private SourceValueType shapeOf(String value) {
        String lower = value.toLowerCase();

        if (BOOLEANS.contains(lower)) {
            /*
             * "1" and "0" are the awkward pair: true and false in a tracking
             * column, quantities in a stock one. Counted as both, so whichever
             * the rest of the column agrees with wins.
             */
            return INTEGER.matcher(value).matches() ? SourceValueType.INTEGER : SourceValueType.BOOLEAN;
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return SourceValueType.URL;
        }
        if (DATETIME.matcher(value).matches()) {
            return SourceValueType.DATETIME;
        }
        if (DATE.matcher(value).matches()) {
            return SourceValueType.DATE;
        }
        if (INTEGER.matcher(value).matches()) {
            return SourceValueType.INTEGER;
        }
        if (DECIMAL.matcher(value).matches()) {
            return SourceValueType.DECIMAL;
        }

        return SourceValueType.TEXT;
    }
}
