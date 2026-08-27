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
                job.getFailureMessage(),
                isCommittable(job)
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
    public boolean isCommittable(ImportJob job) {
        boolean hasSomethingToDo = orZero(job.getValidRows()) > 0 || orZero(job.getDuplicateRows()) > 0;

        return job.getStatus() == ImportStatus.READY && hasSomethingToDo;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
