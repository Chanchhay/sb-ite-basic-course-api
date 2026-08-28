package kh.edu.istad.ite.features.migration.service;

import kh.edu.istad.ite.features.dataimport.field.ImportField;
import kh.edu.istad.ite.features.dataimport.field.ImportFieldRequirement;
import kh.edu.istad.ite.features.migration.dto.AssistedMigrationDtos;
import kh.edu.istad.ite.features.migration.entity.AssistedMigration;
import kh.edu.istad.ite.features.migration.entity.MigrationIssue;
import kh.edu.istad.ite.features.migration.mapping.ColumnSuggestion;
import kh.edu.istad.ite.features.migration.profile.ColumnProfile;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.springframework.stereotype.Component;

import java.util.List;

/** Turns the migration's own records into what the screens read. */
@Component
public class AssistedMigrationMapper {

    public AssistedMigrationDtos.MigrationResponse toResponse(AssistedMigration migration) {
        return new AssistedMigrationDtos.MigrationResponse(
                migration.getId(),
                migration.getBusiness().getId(),
                migration.getMigrationMode(),
                migration.getTargetImportType(),
                migration.getStatus(),
                migration.getSnapshotAt(),
                migration.getSourceType(),
                migration.getSourceFileName(),
                migration.getSourceFileSize(),
                migration.getRowCount(),
                migration.getColumnCount(),
                migration.getSourceColumns(),
                migration.getColumnMappings(),
                migration.getUnresolvedIssueCount(),
                migration.getPreparedImportJobId(),
                migration.getFailureMessage(),
                migration.getCreatedDate()
        );
    }

    public AssistedMigrationDtos.ColumnProfileResponse toResponse(ColumnProfile profile) {
        return new AssistedMigrationDtos.ColumnProfileResponse(
                profile.column(),
                profile.rows(),
                profile.filled(),
                profile.empty(),
                profile.distinct(),
                profile.samples(),
                profile.likelyType()
        );
    }

    public AssistedMigrationDtos.SuggestionResponse toResponse(ColumnSuggestion suggestion) {
        return new AssistedMigrationDtos.SuggestionResponse(
                suggestion.sourceColumn(),
                suggestion.target().name(),
                suggestion.target().getLabel(),
                suggestion.confidence(),
                suggestion.reason()
        );
    }

    public AssistedMigrationDtos.IssueResponse toResponse(MigrationIssue issue) {
        return new AssistedMigrationDtos.IssueResponse(
                issue.getId(),
                issue.getCode(),
                issue.getSeverity(),
                issue.getStatus(),
                issue.getTargetField(),
                issue.getSourceValue(),
                issue.getMessage(),
                issue.getAffectedRows(),
                issue.getSampleRows(),
                issue.getSuggestion(),
                issue.getResolution()
        );
    }

    /**
     * Everything a column may be mapped to, with the required ones marked.
     *
     * Served rather than written into the screen so the list an operator picks
     * from is the same list the importer will accept — a screen offering a
     * field the importer has never heard of is a mapping that fails later.
     */
    public List<AssistedMigrationDtos.TargetFieldResponse> targetFields(ImportTargetType targetType) {
        return ImportField.forTarget(targetType).stream()
                .map(field -> new AssistedMigrationDtos.TargetFieldResponse(
                        field.name(),
                        field.getLabel(),
                        field.getHelp(),
                        ImportField.requiredFor(targetType).contains(field)))
                .toList();
    }
}
