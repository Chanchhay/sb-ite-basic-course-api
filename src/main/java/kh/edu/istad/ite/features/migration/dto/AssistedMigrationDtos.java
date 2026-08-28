package kh.edu.istad.ite.features.migration.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.shared.enums.AssistedMigrationStatus;
import kh.edu.istad.ite.shared.enums.ImportSourceType;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.MigrationIssueSeverity;
import kh.edu.istad.ite.shared.enums.MigrationIssueStatus;
import kh.edu.istad.ite.shared.enums.MigrationMode;
import kh.edu.istad.ite.shared.enums.SourceValueType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the assisted migration screens send and receive.
 *
 * Gathered in one file because they are one conversation — an operator moves
 * through setup, upload, analysis, mapping and review in a single sitting, and
 * splitting nine small records across nine files would make the shape of that
 * conversation harder to see, not easier.
 */
public final class AssistedMigrationDtos {

    private AssistedMigrationDtos() {
    }

    /** @param snapshotAt the moment the source data describes, if known */
    public record CreateRequest(
            @NotNull(message = "targetType cannot be null")
            ImportTargetType targetType,
            LocalDateTime snapshotAt
    ) {
    }

    public record MappingRequest(
            @NotNull(message = "mappings cannot be null")
            Map<String, String> mappings
    ) {
    }

    /** @param resolution free-form, because each kind of issue is decided differently */
    public record ResolutionRequest(
            @NotNull(message = "resolution cannot be null")
            Map<String, Object> resolution
    ) {
    }

    public record MigrationResponse(
            UUID id,
            UUID businessId,
            MigrationMode migrationMode,
            ImportTargetType targetImportType,
            AssistedMigrationStatus status,
            LocalDateTime snapshotAt,
            ImportSourceType sourceType,
            String sourceFileName,
            Long sourceFileSize,
            Integer rowCount,
            Integer columnCount,
            List<String> sourceColumns,
            Map<String, String> columnMappings,
            Integer unresolvedIssueCount,
            UUID preparedImportJobId,
            String failureMessage,
            LocalDateTime createdAt
    ) {
    }

    /** One column, as the file has it. */
    public record ColumnProfileResponse(
            String column,
            int rows,
            int filled,
            int empty,
            int distinct,
            List<String> samples,
            SourceValueType likelyType
    ) {
    }

    /**
     * @param confidence 0 to 1. Shown rather than acted on: an operator who can
     *                   see 71% against 96% knows which to check.
     */
    public record SuggestionResponse(
            String sourceColumn,
            String suggestedField,
            String suggestedFieldLabel,
            double confidence,
            String reason
    ) {
    }

    public record AnalysisResponse(
            int rows,
            List<ColumnProfileResponse> columns,
            List<SuggestionResponse> suggestions,
            List<TargetFieldResponse> targetFields
    ) {
    }

    /** What an operator may map a column to. */
    public record TargetFieldResponse(
            String field,
            String label,
            String help,
            boolean required
    ) {
    }

    public record IssueResponse(
            UUID id,
            String code,
            MigrationIssueSeverity severity,
            MigrationIssueStatus status,
            String targetField,
            String sourceValue,
            String message,
            int affectedRows,
            List<Integer> sampleRows,
            Map<String, Object> suggestion,
            Map<String, Object> resolution
    ) {
    }
}
