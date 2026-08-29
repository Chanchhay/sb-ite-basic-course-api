package kh.edu.istad.ite.features.migration.transform;

import kh.edu.istad.ite.features.dataimport.canonical.DeclaredUnit;

import java.util.List;

/**
 * What a whole file became, plus everything worth saying about it.
 *
 * The rows and the findings come back together because they are one reading of
 * the file: the rows are what would be imported if every finding were
 * accepted, and the findings are what an operator has to agree to first.
 */
public record TransformResult(
        List<PreparedRow> rows,
        List<Finding> findings,
        List<DeclaredUnit> units
) {

    /**
     * One thing worth saying, before it has been grouped and stored.
     *
     * @param sourceValue what caused it, which is what groups it
     * @param rowNumber   the row it was found on
     */
    public record Finding(
            String code,
            String targetField,
            String sourceValue,
            String message,
            int rowNumber,
            boolean blocking
    ) {
    }
}
