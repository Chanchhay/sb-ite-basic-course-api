package kh.edu.istad.ite.features.migration.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import kh.edu.istad.ite.shared.enums.SourceValueType;

import java.util.List;

/**
 * What one column of a customer's file turned out to contain.
 *
 * Kept so that opening the migration again does not mean reading the whole
 * file again — a fifteen thousand row export is not something to re-profile
 * every time someone refreshes a page.
 *
 * @param filled     how many rows had a value here
 * @param distinct   how many different values, which is what separates a
 *                   category column from a name column at a glance
 * @param samples    a few real values, because a heading like "cat" means
 *                   nothing and "Beverages, Snacks, Dairy" means everything
 * @param likelyType the shape of the values, never their meaning
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ColumnProfile(
        String column,
        int rows,
        int filled,
        int distinct,
        List<String> samples,
        SourceValueType likelyType
) {

    public int empty() {
        return Math.max(0, rows - filled);
    }

    /** What share of rows had nothing here, 0 to 1. */
    public double emptyRatio() {
        return rows == 0 ? 0 : (double) empty() / rows;
    }

    /**
     * How close this column comes to identifying a row.
     *
     * One for a column whose every value differs — a SKU, a barcode, a product
     * id — and near zero for one that repeats, like a category.
     */
    public double uniqueRatio() {
        return filled == 0 ? 0 : (double) distinct / filled;
    }
}
