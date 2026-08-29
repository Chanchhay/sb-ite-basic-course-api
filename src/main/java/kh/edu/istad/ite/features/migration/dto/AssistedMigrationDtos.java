package kh.edu.istad.ite.features.migration.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.shared.enums.AssistedMigrationStatus;
import kh.edu.istad.ite.shared.enums.ImportSourceType;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.MigrationIssueSeverity;
import kh.edu.istad.ite.shared.enums.MigrationIssueStatus;
import kh.edu.istad.ite.shared.enums.MigrationMode;
import kh.edu.istad.ite.shared.enums.FieldResolutionSource;
import kh.edu.istad.ite.shared.enums.JoinCardinality;
import kh.edu.istad.ite.shared.enums.MigrationJoinType;
import kh.edu.istad.ite.shared.enums.MigrationSourcePurpose;
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

    /**
     * @param optionAxisNames what to call the option axes, keyed "option1" and
     *                        "option2". Absent means the source column's own
     *                        heading is used, which is where a file keeps that
     *                        word — this is for when the heading is "SZ".
     */
    public record MappingRequest(
            @NotNull(message = "mappings cannot be null")
            Map<String, String> mappings,
            Map<String, String> optionAxisNames
    ) {
    }

    /**
     * What preparing this file would do, before anybody agrees to it.
     *
     * Counted against the shop's own catalogue rather than the file, because
     * "8 categories will be created" and "8 categories are named" are different
     * numbers and only the first is news.
     */
    public record PreparedSummary(
            int items,
            int optionRows,
            int unitsExisting,
            int unitsToCreate,
            int categoriesExisting,
            int categoriesToCreate,
            int possibleDuplicates,
            int alreadyInCatalogue,
            int blocking,
            List<String> unitsToCreateNames,
            List<String> categoriesToCreateNames,
            List<String> sourceFileNames,
            Map<FieldResolutionSource, Integer> resolvedBy
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

    /** One of the customer's files, as the migration holds it. */
    public record SourceResponse(
            UUID id,
            int ordinal,
            boolean primarySource,
            MigrationSourcePurpose purpose,
            ImportSourceType sourceType,
            String fileName,
            Long fileSize,
            String sheetName,
            Integer rowCount,
            Integer columnCount,
            List<String> sourceColumns,
            Map<String, String> columnMappings,
            boolean analyzed
    ) {
    }

    /** @param purpose what this file appears to be for, for the operator to confirm */
    public record AddSourceRequest(MigrationSourcePurpose purpose) {
    }

    public record SourcePurposeRequest(
            @NotNull(message = "purpose cannot be null")
            MigrationSourcePurpose purpose
    ) {
    }

    /** One file's headings pointed at FluxiBiz fields. */
    public record SourceMappingRequest(
            @NotNull(message = "mappings cannot be null")
            Map<String, String> mappings
    ) {
    }

    /**
     * One file's analysis.
     *
     * Returned per file rather than per migration, because that is the unit an
     * operator reads: they look at the stock export and ask what its columns
     * are, not what all four files' columns are together.
     */
    public record SourceAnalysisResponse(
            UUID sourceId,
            String fileName,
            MigrationSourcePurpose purpose,
            int rows,
            List<ColumnProfileResponse> columns,
            List<SuggestionResponse> suggestions
    ) {
    }

    public record AnalysisSummaryResponse(
            List<SourceAnalysisResponse> sources,
            List<TargetFieldResponse> targetFields
    ) {
    }

    /**
     * A pair of columns that look like they identify the same records.
     *
     * The quality travels with the suggestion so an operator never has to
     * approve a join and then find out what it did. Both are shown at once,
     * which is the only order in which the decision is informed.
     */
    public record JoinSuggestionResponse(
            UUID leftSourceId,
            String leftSourceName,
            String leftColumn,
            UUID rightSourceId,
            String rightSourceName,
            String rightColumn,
            double confidence,
            String reason,
            JoinQualityResponse quality
    ) {
    }

    /** @param usable false when both sides repeat, which no join can carry out honestly */
    public record JoinQualityResponse(
            UUID relationshipId,
            UUID leftSourceId,
            String leftSourceName,
            String leftColumn,
            UUID rightSourceId,
            String rightSourceName,
            String rightColumn,
            MigrationJoinType joinType,
            int leftRows,
            int rightRows,
            int matchedLeftRows,
            int unmatchedLeftRows,
            int unmatchedRightRows,
            int duplicateLeftKeys,
            int duplicateRightKeys,
            JoinCardinality cardinality,
            boolean usable
    ) {
    }

    public record RelationshipRequest(
            @NotNull(message = "leftSourceId cannot be null")
            UUID leftSourceId,
            @NotNull(message = "leftColumn cannot be null")
            String leftColumn,
            @NotNull(message = "rightSourceId cannot be null")
            UUID rightSourceId,
            @NotNull(message = "rightColumn cannot be null")
            String rightColumn,
            MigrationJoinType joinType
    ) {
    }

    public record RelationshipsRequest(
            @NotNull(message = "relationships cannot be null")
            List<RelationshipRequest> relationships
    ) {
    }

    /**
     * One field, and how much of it the customer's files actually supplied.
     *
     * @param behaviour  what absence means here — required, derivable,
     *                   answerable once for the whole migration, or harmless
     * @param resolvedBy how the values that do exist were arrived at
     */
    public record MissingFieldResponse(
            String field,
            String label,
            String behaviour,
            int filled,
            int missing,
            boolean blocking,
            String suggestion,
            String question,
            List<String> samples,
            Map<FieldResolutionSource, Integer> resolvedBy
    ) {
    }

    public record MissingFieldsResponse(
            List<MissingFieldResponse> fields,
            Map<FieldResolutionSource, Integer> resolvedBy
    ) {
    }

    /**
     * Why one record came out the way it did.
     *
     * @param originalValue what the source actually held, before it was read
     */
    public record FieldExplanation(
            String field,
            String label,
            String value,
            FieldResolutionSource resolution,
            String sourceFile,
            String sourceColumn,
            Integer sourceRow,
            String originalValue,
            String rule
    ) {
    }

    public record RowExplanation(
            int rowNumber,
            String name,
            List<FieldExplanation> fields
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
