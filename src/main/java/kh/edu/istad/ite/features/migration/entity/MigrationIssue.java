package kh.edu.istad.ite.features.migration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.shared.enums.MigrationIssueSeverity;
import kh.edu.istad.ite.shared.enums.MigrationIssueStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One thing about the source data, said once however many rows it touches.
 *
 * The shape of this record is the whole argument of the review step. A file of
 * fifteen thousand rows where four hundred and fifty say "SACK" has one
 * problem, not four hundred and fifty — and an operator asked the same question
 * four hundred and fifty times will answer the first few carefully and the rest
 * by reflex.
 *
 * So issues are grouped by what actually has to be decided: a field and the
 * source value that caused it. The decision is recorded here, and applies to
 * every row the group covers.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "migration_issues",
        indexes = {
                @Index(name = "idx_migration_issues_migration", columnList = "migration_id, severity")
        }
)
public class MigrationIssue extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "migration_id", nullable = false)
    private AssistedMigration migration;

    /** What kind of thing this is, for grouping and for the screen to key on. */
    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private MigrationIssueSeverity severity = MigrationIssueSeverity.REVIEW_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MigrationIssueStatus status = MigrationIssueStatus.OPEN;

    /** The FluxiBiz field this is about, where it is about one. */
    @Column(name = "target_field", length = 60)
    private String targetField;

    /**
     * The source value every row in this group shared — "SACK", "$0.75".
     *
     * What makes the group a group, and what a resolution is remembered
     * against when the file is transformed again.
     */
    @Column(name = "source_value", length = 500)
    private String sourceValue;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "affected_rows", nullable = false)
    private Integer affectedRows = 0;

    /** A few real row numbers, so an operator can go and look. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sample_rows", columnDefinition = "jsonb")
    private List<Integer> sampleRows = new ArrayList<>();

    /** What we would do, if anything. Shown as a default, never applied alone. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestion", columnDefinition = "jsonb")
    private Map<String, Object> suggestion = new LinkedHashMap<>();

    /**
     * What the operator decided, in the same shape as the suggestion.
     *
     * Kept rather than applied and forgotten: transforming a file again must
     * reach the same answer without asking twice, and anyone reviewing the
     * migration afterwards needs to see who decided what.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolution", columnDefinition = "jsonb")
    private Map<String, Object> resolution = new LinkedHashMap<>();

    /** Whether this still stands between the operator and a prepared import. */
    public boolean isBlocking() {
        return status == MigrationIssueStatus.OPEN
                && (severity == MigrationIssueSeverity.REVIEW_REQUIRED
                    || severity == MigrationIssueSeverity.ERROR);
    }
}
