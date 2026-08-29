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
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.shared.enums.AssistedMigrationStatus;
import kh.edu.istad.ite.shared.enums.ImportSourceType;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.MigrationMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One attempt to bring a customer's own export into FluxiBiz on their behalf.
 *
 * Sits in front of the importer every shop already uses rather than beside it.
 * A shopkeeper filling in our template has already done the hard part — deciding
 * what each column means — and the importer can take them at their word. A
 * migration starts from a file nobody here has seen, written by a system nobody
 * here controls, and its whole job is to work that out and then hand the result
 * to the same importer.
 *
 * So this holds the working-out, and nothing else. It never writes an item, a
 * category, a unit or a stock entry: when it is finished it produces an import
 * job, and the ordinary checking, review and commit take over. That is what
 * keeps one authoritative path into a shop's catalogue however the data
 * arrived.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "assisted_migrations",
        indexes = {
                @Index(
                        name = "idx_assisted_migrations_business",
                        columnList = "business_owner_id, created_date"
                )
        }
)
public class AssistedMigration extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    /**
     * Which question this migration answers.
     *
     * Only a state migration is carried out today, but it is recorded rather
     * than assumed — a job that never said what it was would have to be guessed
     * at the moment history and live sync exist.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "migration_mode", nullable = false, length = 40)
    private MigrationMode migrationMode = MigrationMode.STATE_MIGRATION;

    /**
     * The moment the source data describes.
     *
     * A stock count is only true at an instant. Recording which instant is what
     * later lets a delta migration ask "what changed since?" — and, long before
     * that, lets anyone reading a finished migration know whether the numbers
     * were a week stale when they landed.
     */
    @Column(name = "snapshot_at")
    private LocalDateTime snapshotAt;

    /** Which of FluxiBiz's own importers this will eventually feed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_import_type", nullable = false, length = 40)
    private ImportTargetType targetImportType = ImportTargetType.ITEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AssistedMigrationStatus status = AssistedMigrationStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 40)
    private ImportSourceType sourceType;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_file_size")
    private Long sourceFileSize;

    /**
     * Where the customer's own file is kept, exactly as they sent it.
     *
     * Never rewritten. Everything this feature learns is stored beside the
     * file rather than in it, so an operator who suspects the pipeline has
     * misread something can always go back to what actually arrived.
     */
    @Column(name = "raw_object_key", length = 500)
    private String rawObjectKey;

    /** Which sheet of a workbook the rows were read from. */
    @Column(name = "source_sheet", length = 255)
    private String sourceSheet;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "column_count")
    private Integer columnCount;

    /** The headings as they appeared, in order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_columns", columnDefinition = "jsonb")
    private List<String> sourceColumns = new ArrayList<>();

    /**
     * What the operator settled on: source heading to FluxiBiz field.
     *
     * Suggestions live in the analysis; this is the decision. A column the
     * operator chose to ignore is absent rather than mapped to nothing.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mappings", columnDefinition = "jsonb")
    private Map<String, String> columnMappings = new LinkedHashMap<>();

    /**
     * What the option columns are called, when the operator disagrees with us.
     *
     * A source file names its axes in the headings — a SIZE column holding
     * "Small" — so the heading is the axis name and the cell is its value. That
     * is right often enough to default to and wrong often enough to override:
     * a column called "SZ" or "attr1" needs a person to say what it measures,
     * and FluxiBiz shows that word to shoppers.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_axis_names", columnDefinition = "jsonb")
    private Map<String, String> optionAxisNames = new LinkedHashMap<>();

    /** How many issues still need a person. Zero is what READY means. */
    @Column(name = "unresolved_issue_count")
    private Integer unresolvedIssueCount = 0;

    /**
     * The import job this migration produced, once it has produced one.
     *
     * The handover point, and the reason nothing here writes to the catalogue.
     */
    @Column(name = "prepared_import_job_id")
    private UUID preparedImportJobId;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;
}
