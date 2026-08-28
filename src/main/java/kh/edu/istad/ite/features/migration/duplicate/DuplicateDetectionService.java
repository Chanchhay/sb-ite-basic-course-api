package kh.edu.istad.ite.features.migration.duplicate;

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
 * Finds the same product listed twice, without ever deciding it is.
 *
 * Two quite different jobs behind one name. An identifier repeating — the same
 * SKU on two rows — is a fact, and can be reported as one. Two names that look
 * alike is an opinion: "Coca Cola 330ML" and "Coca-Cola 330 ml" are almost
 * certainly one product, and "Coffee" and "Coffee Equipment" are certainly not,
 * and nothing here can tell which case it is looking at. So names are raised
 * for a person and never merged.
 *
 * Comparing every row with every other would be forty million comparisons on a
 * twenty thousand row file. Instead rows are bucketed by something cheap that
 * near-duplicates must share — their first meaningful word — and compared only
 * within a bucket, which keeps it near linear on real catalogues.
 */
@Component
public class DuplicateDetectionService {

    /** Alike enough to ask about; below this, two products that share a word. */
    private static final double SIMILAR_ENOUGH = 0.85;

    /**
     * A bucket bigger than this is a word like "the" — comparing inside it
     * costs more than it finds, and everything it would find is a coincidence.
     */
    private static final int MAX_BUCKET = 60;

    public List<TransformResult.Finding> findWithin(List<PreparedRow> rows) {
        List<TransformResult.Finding> findings = new ArrayList<>();

        findRepeatedIdentifiers(rows, ImportField.SKU, findings);
        findRepeatedIdentifiers(rows, ImportField.BARCODE, findings);
        findSimilarNames(rows, findings);

        return findings;
    }

    /**
     * Rows that match something the shop already has.
     *
     * The other half of duplicate detection, and the half that matters on a
     * second attempt: a migration re-run after a correction should say "you
     * already have these forty" rather than quietly making forty more.
     *
     * The whole catalogue is read once into maps rather than asked per row. A
     * fifteen thousand row file against a two thousand item shop is one query
     * and thirty thousand lookups, not thirty thousand queries.
     *
     * @param existing what the shop has now, keyed however it can be recognised
     */
    public List<TransformResult.Finding> findAgainstCatalogue(
            List<PreparedRow> rows,
            ExistingCatalogue existing
    ) {
        List<TransformResult.Finding> findings = new ArrayList<>();

        for (PreparedRow row : rows) {
            String sku = row.get(ImportField.SKU);
            String name = row.get(ImportField.NAME);

            if (sku != null && existing.hasSku(sku)) {
                findings.add(new TransformResult.Finding(
                        "ALREADY_IN_CATALOGUE",
                        ImportField.SKU.name(),
                        sku.trim(),
                        "This shop already has an item with SKU \"" + sku.trim()
                                + "\". The import will update it rather than add another.",
                        row.sourceRowNumber(),
                        false));
                continue;
            }

            /*
             * Only worth mentioning when the SKU did not already answer it. A
             * name match without a SKU match is the ambiguous case — same
             * product renamed, or two products that happen to share a name —
             * and it is raised rather than decided.
             */
            if (name != null && existing.hasName(name)) {
                findings.add(new TransformResult.Finding(
                        "NAME_ALREADY_IN_CATALOGUE",
                        ImportField.NAME.name(),
                        name.trim(),
                        "This shop already has an item called \"" + name.trim()
                                + "\", with a different code. Check whether these are the same item.",
                        row.sourceRowNumber(),
                        false));
            }
        }

        return findings;
    }

    /**
     * What the shop already has, read once.
     *
     * A small record rather than a repository call per row: the point of
     * gathering it is that nothing downstream needs the database again.
     */
    public record ExistingCatalogue(Set<String> skus, Set<String> names) {

        public static ExistingCatalogue of(List<String> skus, List<String> names) {
            return new ExistingCatalogue(normalized(skus), normalized(names));
        }

        private static Set<String> normalized(List<String> values) {
            Set<String> out = new LinkedHashSet<>();

            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .forEach(out::add);

            return out;
        }

        boolean hasSku(String sku) {
            return skus.contains(sku.trim().toLowerCase(Locale.ROOT));
        }

        boolean hasName(String name) {
            return names.contains(name.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * The same SKU or barcode on two rows.
     *
     * Not a judgement: an identifier is the shop's own promise that two things
     * are the same thing, and a file breaking that promise is telling us
     * something is wrong with it rather than with our reading.
     */
    private void findRepeatedIdentifiers(
            List<PreparedRow> rows,
            ImportField field,
            List<TransformResult.Finding> findings
    ) {
        Map<String, List<Integer>> byValue = new LinkedHashMap<>();

        for (PreparedRow row : rows) {
            String value = row.get(field);

            if (value != null && !value.isBlank()) {
                byValue.computeIfAbsent(value.trim().toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                        .add(row.sourceRowNumber());
            }
        }

        byValue.forEach((value, lines) -> {
            if (lines.size() < 2) {
                return;
            }

            findings.add(new TransformResult.Finding(
                    "DUPLICATE_IDENTIFIER",
                    field.name(),
                    value,
                    field.getLabel() + " \"" + value + "\" is on " + lines.size()
                            + " rows. Decide whether these are one item or several.",
                    lines.getFirst(),
                    true));
        });
    }

    /**
     * Names alike enough to be worth a second look.
     *
     * Never merged, and deliberately not blocking: a shop genuinely may sell
     * "Coke 330ml" and "Coke 330ml Diet", and an import that refused to proceed
     * until somebody swore they were different would be worse than one that
     * mentions it.
     */
    private void findSimilarNames(List<PreparedRow> rows, List<TransformResult.Finding> findings) {
        Map<String, List<PreparedRow>> buckets = new LinkedHashMap<>();

        for (PreparedRow row : rows) {
            String name = row.get(ImportField.NAME);

            if (name == null || name.isBlank()) {
                continue;
            }

            buckets.computeIfAbsent(bucketOf(name), key -> new ArrayList<>()).add(row);
        }

        Set<Integer> alreadyPaired = new LinkedHashSet<>();

        for (List<PreparedRow> bucket : buckets.values()) {
            if (bucket.size() < 2 || bucket.size() > MAX_BUCKET) {
                continue;
            }

            for (int left = 0; left < bucket.size(); left++) {
                for (int right = left + 1; right < bucket.size(); right++) {
                    PreparedRow one = bucket.get(left);
                    PreparedRow other = bucket.get(right);

                    if (alreadyPaired.contains(other.sourceRowNumber())) {
                        continue;
                    }

                    String first = one.get(ImportField.NAME);
                    String second = other.get(ImportField.NAME);
                    double similarity = similarity(first, second);

                    if (similarity < SIMILAR_ENOUGH) {
                        continue;
                    }

                    alreadyPaired.add(other.sourceRowNumber());

                    findings.add(new TransformResult.Finding(
                            "POSSIBLE_DUPLICATE",
                            ImportField.NAME.name(),
                            first,
                            "\"" + first + "\" and \"" + second + "\" look like the same item ("
                                    + Math.round(similarity * 100) + "% alike). Rows "
                                    + one.sourceRowNumber() + " and " + other.sourceRowNumber() + ".",
                            one.sourceRowNumber(),
                            false));
                }
            }
        }
    }

    /**
     * What two near-identical names must have in common.
     *
     * The first word with letters in it, stripped of punctuation. "Coca Cola
     * 330ML" and "Coca-Cola 330 ml" both give "coca", so they meet; two
     * unrelated products almost never do, so the expensive comparison runs on
     * a handful of rows rather than the file.
     */
    private String bucketOf(String name) {
        String[] words = normalize(name).split(" ");

        for (String word : words) {
            if (word.length() >= 3) {
                return word;
            }
        }

        return words.length == 0 ? "" : words[0];
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * How alike two names are, by the words they share.
     *
     * Words rather than characters, because that is how these differ in
     * practice — punctuation and spacing move about while the words stay put.
     * "Coca Cola 330ml" against "Coca-Cola 330 ml" is three words out of three.
     */
    private double similarity(String left, String right) {
        Set<String> ours = new LinkedHashSet<>(List.of(normalize(left).split(" ")));
        Set<String> theirs = new LinkedHashSet<>(List.of(normalize(right).split(" ")));

        if (ours.isEmpty() || theirs.isEmpty()) {
            return 0;
        }

        Set<String> shared = new LinkedHashSet<>(ours);
        shared.retainAll(theirs);

        Set<String> combined = new LinkedHashSet<>(ours);
        combined.addAll(theirs);

        return (double) shared.size() / combined.size();
    }
}
