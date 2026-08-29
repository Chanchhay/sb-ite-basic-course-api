package kh.edu.istad.ite.features.migration.join;

import kh.edu.istad.ite.features.dataimport.parser.SourceRow;
import kh.edu.istad.ite.shared.enums.JoinCardinality;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Counts what a join would do, so an operator agrees to a number rather than a hope.
 *
 * Everything here is derived from two hash maps built once each. Nothing scans
 * one file per row of the other, which matters: the check has to be cheap
 * enough to run while the operator is still choosing, or nobody will run it.
 */
@Component
public class JoinAnalysisService {

    public JoinQuality analyse(
            List<SourceRow> leftRows,
            String leftColumn,
            List<SourceRow> rightRows,
            String rightColumn
    ) {
        Map<String, List<SourceRow>> left = JoinKeys.index(leftRows, leftColumn);
        Map<String, List<SourceRow>> right = JoinKeys.index(rightRows, rightColumn);

        int matchedLeft = 0;

        for (Map.Entry<String, List<SourceRow>> entry : left.entrySet()) {
            if (right.containsKey(entry.getKey())) {
                matchedLeft += entry.getValue().size();
            }
        }

        int unmatchedRight = 0;

        for (Map.Entry<String, List<SourceRow>> entry : right.entrySet()) {
            if (!left.containsKey(entry.getKey())) {
                unmatchedRight += entry.getValue().size();
            }
        }

        int duplicateLeft = countRepeated(left);
        int duplicateRight = countRepeated(right);

        /*
         * Rows without a value in the key column never reach either map, and
         * they are unmatched by definition — a stock line with no product code
         * belongs to nothing. Counting them against the left total rather than
         * quietly dropping them is what makes "150 products without stock"
         * add up for the person reading it.
         */
        return new JoinQuality(
                leftRows.size(),
                rightRows.size(),
                matchedLeft,
                leftRows.size() - matchedLeft,
                unmatchedRight,
                duplicateLeft,
                duplicateRight,
                cardinalityOf(duplicateLeft, duplicateRight));
    }

    private int countRepeated(Map<String, List<SourceRow>> index) {
        return (int) index.values().stream().filter(rows -> rows.size() > 1).count();
    }

    private JoinCardinality cardinalityOf(int duplicateLeft, int duplicateRight) {
        if (duplicateLeft > 0 && duplicateRight > 0) {
            return JoinCardinality.MANY_TO_MANY;
        }

        if (duplicateRight > 0) {
            return JoinCardinality.ONE_TO_MANY;
        }

        if (duplicateLeft > 0) {
            return JoinCardinality.MANY_TO_ONE;
        }

        return JoinCardinality.ONE_TO_ONE;
    }
}
