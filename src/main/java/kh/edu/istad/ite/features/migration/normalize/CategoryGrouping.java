package kh.edu.istad.ite.features.migration.normalize;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.migration.transform.PreparedRow;
import kh.edu.istad.ite.features.migration.transform.TransformResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Notices when one category has been written several ways.
 *
 * Old systems accumulate "Beverage", "beverages" and "BEVERAGES" as three
 * shelves because nothing ever stopped them, and importing all three gives the
 * shop a mess to tidy by hand afterwards.
 *
 * It only ever suggests. Case and spacing are safe to call the same thing;
 * "Coffee" and "Coffee Equipment" are not, and no rule can tell those apart
 * from the outside — so this groups what differs by case, spacing or
 * punctuation alone, and leaves everything else where it is.
 */
@Component
public class CategoryGrouping {

    public List<TransformResult.Finding> find(List<PreparedRow> rows) {
        List<TransformResult.Finding> findings = new ArrayList<>();

        findVariants(rows, ImportField.ITEM_GROUP, findings);
        findVariants(rows, ImportField.PARENT_GROUP, findings);

        return findings;
    }

    private void findVariants(
            List<PreparedRow> rows,
            ImportField field,
            List<TransformResult.Finding> findings
    ) {
        Map<String, Set<String>> spellings = new LinkedHashMap<>();
        Map<String, Integer> firstRow = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (PreparedRow row : rows) {
            String value = row.get(field);

            if (value == null || value.isBlank()) {
                continue;
            }

            String key = key(value);

            spellings.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(value.trim());
            firstRow.putIfAbsent(key, row.sourceRowNumber());
            counts.merge(key, 1, Integer::sum);
        }

        spellings.forEach((key, written) -> {
            if (written.size() < 2) {
                return;
            }

            /*
             * The commonest spelling wins by default. It is the one the shop
             * uses most, which is the closest thing to their own preference
             * that the file contains — and the operator can say otherwise.
             */
            String preferred = written.iterator().next();

            findings.add(new TransformResult.Finding(
                    "CATEGORY_SPELLINGS",
                    field.name(),
                    key,
                    field.getLabel() + " \"" + preferred + "\" is written "
                            + written.size() + " ways in this file (" + String.join(", ", written)
                            + "), covering " + counts.get(key) + " rows. Import as one?",
                    firstRow.get(key),
                    false));
        });
    }

    /**
     * Two spellings of one shelf.
     *
     * Trailing s included, so "Beverage" and "Beverages" meet — the commonest
     * split of all, and one no shop means. Anything needing more than case,
     * spacing, punctuation and a plural is a different word, and a different
     * word is a different category until somebody says otherwise.
     */
    private String key(String value) {
        String flattened = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .trim();

        return flattened.endsWith("s") && flattened.length() > 3
                ? flattened.substring(0, flattened.length() - 1)
                : flattened;
    }
}
