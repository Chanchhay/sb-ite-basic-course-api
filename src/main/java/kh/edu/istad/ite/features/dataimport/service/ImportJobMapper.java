package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.features.dataimport.dto.ImportJobResponse;
import kh.edu.istad.ite.features.dataimport.dto.ImportRowResponse;
import kh.edu.istad.ite.features.dataimport.entity.ImportJob;
import kh.edu.istad.ite.features.dataimport.entity.ImportRow;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns import records into what the dashboard reads.
 *
 * Hand-written rather than generated: almost every field on the way out is
 * either renamed for the screen or worked out from several on the way in, and
 * the one that matters most — whether Import may be pressed — is a rule, not a
 * copy.
 */
@Component
public class ImportJobMapper {

    public ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getTargetType(),
                job.getSourceType(),
                job.getStatus(),
                job.getSourceFileName(),
                job.getSourceFileSize(),
                job.getSourceColumns(),
                job.getColumnMappings(),
                job.getDuplicateStrategy(),
                job.getDefaultUnitId(),
                job.getDefaultItemType(),
                orZero(job.getTotalRows()),
                orZero(job.getValidRows()),
                orZero(job.getInvalidRows()),
                orZero(job.getDuplicateRows()),
                orZero(job.getCreatedRows()),
                orZero(job.getUpdatedRows()),
                orZero(job.getSkippedRows()),
                orZero(job.getFailedRows()),
                orZero(job.getCreatedItemGroups()),
                orZero(job.getCreatedStockEntries()),
                job.getCreatedBy(),
                job.getCreatedDate(),
                job.getValidationStartedAt(),
                job.getValidationCompletedAt(),
                job.getCommitStartedAt(),
                job.getCommitCompletedAt(),
                job.getRevertedAt(),
                job.getFailureMessage(),
                isCommittable(job),
                isRevertable(job)
        );
    }

    public ImportRowResponse toResponse(ImportRow row) {
        List<RowIssue> issues = row.getIssues() == null ? List.of() : row.getIssues();

        return new ImportRowResponse(
                row.getId(),
                row.getRowNumber(),
                row.getStatus(),
                row.getRawData(),
                row.getNormalizedData(),
                issues,
                (int) issues.stream().filter(RowIssue::isError).count(),
                (int) issues.stream().filter(issue -> !issue.isError()).count(),
                row.getCommittedEntityId()
        );
    }

    /**
     * Whether the shop may press Import.
     *
     * Two conditions, and both are the point of the feature. The file has to
     * have been checked and come out the other side, and there has to be
     * something worth importing — offering to import a file in which every row
     * failed would be an empty promise.
     */
    /**
     * Only a finished import, and only while it still has creations standing.
     *
     * An import that created nothing has nothing to take away — every row of it
     * either updated an item that existed before or was skipped, and undoing
     * those is not something this can offer.
     */
    public boolean isRevertable(ImportJob job) {
        /*
         * An undone import can be undone again, because an undo does not always
         * finish the job: items that could not be deleted the first time are
         * left standing and go on counting as created. Once whatever held them
         * is dealt with, running it again clears the rest — and the rows it
         * already reverted no longer say they were created, so it picks up
         * exactly where the last one stopped.
         */
        boolean finished = job.getStatus() == ImportStatus.COMMITTED
                || job.getStatus() == ImportStatus.REVERTED;

        return finished && orZero(job.getCreatedRows()) > 0;
    }

    public boolean isCommittable(ImportJob job) {
        boolean hasSomethingToDo = orZero(job.getValidRows()) > 0 || orZero(job.getDuplicateRows()) > 0;

        return job.getStatus() == ImportStatus.READY && hasSomethingToDo;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
