package kh.edu.istad.ite.features.migration.join;

import kh.edu.istad.ite.features.dataimport.parser.SourceRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How two files are matched on a column, and nothing more clever than that.
 *
 * Keys are compared trimmed and case-folded, because "P001" and "p001" are the
 * same product code in every export anyone has ever sent and treating them as
 * two would strand half a stock file. Nothing else is done to them: no
 * stripping of leading zeroes, no ignoring of punctuation. "0012" and "12" are
 * different codes in plenty of systems, and a join that quietly decided
 * otherwise would put one product's stock onto another's.
 */
public final class JoinKeys {

    private JoinKeys() {
    }

    public static String of(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();

        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Every row of a file, filed under its key.
     *
     * A hash map built once, not a scan repeated per row. Two files of
     * twenty thousand rows join in forty thousand steps this way and in four
     * hundred million the obvious way, which is the difference between a
     * migration that prepares while the operator watches and one that times
     * out.
     */
    public static Map<String, List<SourceRow>> index(List<SourceRow> rows, String column) {
        Map<String, List<SourceRow>> byKey = new LinkedHashMap<>();

        for (SourceRow row : rows) {
            String key = of(row.value(column));

            if (key == null) {
                continue;
            }

            byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        return byKey;
    }
}
