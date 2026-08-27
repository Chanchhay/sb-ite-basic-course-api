package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.shared.enums.ImportRowStatus;

import java.util.List;
import java.util.UUID;

/**
 * What checking concluded about one row.
 *
 * @param matchedEntityId what this row already exists as, when it is a
 *                        duplicate — carried forward so the commit does not
 *                        have to work out the match a second time, and so the
 *                        preview can say what would be updated
 */
public record RowVerdict(
        ImportRowStatus status,
        List<RowIssue> issues,
        UUID matchedEntityId
) {

    public static RowVerdict valid(List<RowIssue> issues) {
        return new RowVerdict(ImportRowStatus.VALID, issues, null);
    }

    public static RowVerdict invalid(List<RowIssue> issues) {
        return new RowVerdict(ImportRowStatus.INVALID, issues, null);
    }

    public static RowVerdict duplicate(List<RowIssue> issues, UUID matchedEntityId) {
        return new RowVerdict(ImportRowStatus.DUPLICATE, issues, matchedEntityId);
    }
}
