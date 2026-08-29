package kh.edu.istad.ite.features.migration.mapping;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.field.ImportFieldType;
import kh.edu.istad.ite.features.migration.profile.ColumnProfile;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.SourceValueType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out which FluxiBiz field each column of a stranger's file is.
 *
 * The shop's own importer matches headings exactly and refuses to guess, and
 * that is right for a shopkeeper who downloaded our template: a wrong guess
 * there costs them more to find and undo than leaving a column unmatched. This
 * is the opposite situation. Nobody wrote {@code prd_desc} expecting us to
 * read it, an operator is watching, and every column they have to match by
 * hand is a column they might match wrongly.
 *
 * So it guesses, shows its working, and is only allowed to fill anything in
 * when it is nearly certain. Three things feed a score: what the heading looks
 * like, what shape the values are, and how the column behaves — a column whose
 * every value differs is an identifier, one that repeats is a category.
 *
 * Deliberately arithmetic rather than a model. The same file must map the same
 * way twice, an operator has to be able to argue with the reason, and a
 * customer's catalogue is not something to send to anybody else's service.
 */
@Component
public class ColumnMappingService {

    /** Headings meaning the same field, beyond the aliases the importer knows. */
    private static final Map<ImportField, List<String>> EXTRA_ALIASES = Map.ofEntries(
            Map.entry(ImportField.NAME, List.of(
                    "prddesc", "productdescription", "itemdescription", "descriptionshort",
                    "prdname", "particulars", "articlename", "goods", "menuitem")),
            Map.entry(ImportField.SKU, List.of(
                    "prdcode", "prdid", "productid", "itemid", "articleid", "matcode", "plu")),
            Map.entry(ImportField.BARCODE, List.of("barcodeno", "eannumber", "scan")),
            Map.entry(ImportField.ITEM_GROUP, List.of(
                    "cat", "subcat", "subcategory", "grp", "categorydesc", "class", "line")),
            Map.entry(ImportField.PARENT_GROUP, List.of(
                    "dept", "department", "maincat", "parentcat", "division", "group1")),
            Map.entry(ImportField.UNIT, List.of("uom", "unitofmeasure", "measurement", "packsize")),
            Map.entry(ImportField.PRICE, List.of(
                    "sellp", "sellprice", "sellingprice", "retail", "priceout", "unitprice", "mrp")),
            Map.entry(ImportField.COST_PRICE, List.of(
                    "cost", "costp", "buyprice", "purchaseprice", "pricein", "landedcost")),
            Map.entry(ImportField.OPENING_STOCK, List.of(
                    "balance", "balanceqty", "onhand", "stockonhand", "soh", "qtyonhand", "closingbalance")),
            Map.entry(ImportField.TRACK_INVENTORY, List.of("trackqty", "isstock", "stocked", "inventory")),
            Map.entry(ImportField.LOW_STOCK_LEVEL, List.of("reorder", "reorderlevel", "minqty", "minimum")),
            Map.entry(ImportField.OPTION_GROUP_KEY, List.of("parentproduct", "masterid", "templateid")),
            Map.entry(ImportField.IMAGE_URL, List.of("img", "imgurl", "picurl", "photourl"))
    );

    /**
     * Every column's best guess, with each field claimed at most once.
     *
     * Claimed by the strongest candidate rather than the first, because a file
     * with both {@code name} and {@code prd_desc} should give the name to
     * whichever actually looks like one.
     */
    public List<ColumnSuggestion> suggest(List<ColumnProfile> profiles, ImportTargetType targetType) {
        List<ColumnSuggestion> all = new ArrayList<>();

        for (ColumnProfile profile : profiles) {
            for (ImportField field : ImportField.forTarget(targetType)) {
                double score = score(profile, field);

                if (score >= ColumnSuggestion.MEDIUM) {
                    all.add(new ColumnSuggestion(profile.column(), field, score, reason(profile, field)));
                }
            }
        }

        all.sort(Comparator.comparingDouble(ColumnSuggestion::confidence).reversed());

        Set<ImportField> taken = new HashSet<>();
        Set<String> spoken = new HashSet<>();
        Map<String, ColumnSuggestion> best = new LinkedHashMap<>();

        for (ColumnSuggestion suggestion : all) {
            if (taken.contains(suggestion.target()) || spoken.contains(suggestion.sourceColumn())) {
                continue;
            }

            taken.add(suggestion.target());
            spoken.add(suggestion.sourceColumn());
            best.put(suggestion.sourceColumn(), suggestion);
        }

        return profiles.stream()
                .map(profile -> best.get(profile.column()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * How much this column looks like this field.
     *
     * The heading carries most of the weight, because it is the only thing the
     * person who wrote the file was trying to say. Shape and behaviour adjust
     * rather than decide: they are good at ruling a match out — a text column
     * is not a price — and poor at ruling one in, since every money column in
     * the file looks alike.
     */
    private double score(ColumnProfile profile, ImportField field) {
        double heading = headingScore(profile.column(), field);

        if (heading == 0) {
            return 0;
        }

        double score = heading;

        if (typeAgrees(profile.likelyType(), field.getType())) {
            score += 0.06;
        } else if (typeConflicts(profile.likelyType(), field.getType())) {
            /*
             * A heading can be a coincidence; the values rarely are. "balance"
             * over a column of names is not opening stock, whatever it is
             * called.
             */
            score -= 0.35;
        }

        if (field == ImportField.SKU || field == ImportField.BARCODE) {
            // An identifier that repeats is not identifying anything.
            score += profile.uniqueRatio() > 0.95 ? 0.05 : -0.20;
        }

        if (field == ImportField.ITEM_GROUP || field == ImportField.PARENT_GROUP) {
            // A category that never repeats is a name by another word.
            score += profile.uniqueRatio() < 0.5 ? 0.04 : -0.25;
        }

        if (field == ImportField.NAME && profile.emptyRatio() > 0.05) {
            // Shops do not leave products unnamed; a gappy column is something else.
            score -= 0.20;
        }

        return Math.max(0, Math.min(1, score));
    }

    /**
     * What the heading alone is worth.
     *
     * Exact matches on the importer's own aliases are as good as it gets. Past
     * that, a heading that contains a known alias as a word — "item_code_2" —
     * is worth less than one that is the alias, and a heading matching nothing
     * scores zero however suggestive its values, because a column we cannot
     * name is one an operator should look at rather than one we should assume.
     */
    private double headingScore(String heading, ImportField field) {
        String normalized = ImportField.normalize(heading);

        if (normalized.isEmpty()) {
            return 0;
        }

        if (field.getAliases().contains(normalized)) {
            return 0.95;
        }

        if (EXTRA_ALIASES.getOrDefault(field, List.of()).contains(normalized)) {
            return 0.88;
        }

        for (String alias : field.getAliases()) {
            if (alias.length() >= 4 && normalized.contains(alias)) {
                return 0.72;
            }
        }

        for (String alias : EXTRA_ALIASES.getOrDefault(field, List.of())) {
            if (alias.length() >= 4 && normalized.contains(alias)) {
                return 0.68;
            }
        }

        return 0;
    }

    private boolean typeAgrees(SourceValueType source, ImportFieldType target) {
        return switch (target) {
            case MONEY, NUMBER -> source == SourceValueType.DECIMAL || source == SourceValueType.INTEGER;
            case BOOLEAN -> source == SourceValueType.BOOLEAN;
            case TEXT, ENUM -> source == SourceValueType.TEXT;
        };
    }

    private boolean typeConflicts(SourceValueType source, ImportFieldType target) {
        if (source == SourceValueType.UNKNOWN) {
            return false;
        }

        return switch (target) {
            case MONEY, NUMBER -> source == SourceValueType.TEXT
                    || source == SourceValueType.DATE
                    || source == SourceValueType.DATETIME;
            case BOOLEAN -> source == SourceValueType.TEXT || source == SourceValueType.URL;
            case TEXT, ENUM -> false;
        };
    }

    private String reason(ColumnProfile profile, ImportField field) {
        String normalized = ImportField.normalize(profile.column());

        if (field.getAliases().contains(normalized)
                || EXTRA_ALIASES.getOrDefault(field, List.of()).contains(normalized)) {
            return "\"" + profile.column() + "\" is a name we know for " + field.getLabel() + ".";
        }

        return "\"" + profile.column() + "\" reads like " + field.getLabel()
                + ", and its values are the right shape.";
    }
}
