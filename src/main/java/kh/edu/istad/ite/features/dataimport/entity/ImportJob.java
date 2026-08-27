package kh.edu.istad.ite.features.dataimport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.ImportDuplicateStrategy;
import kh.edu.istad.ite.shared.enums.ImportSourceType;
import kh.edu.istad.ite.shared.enums.ImportStatus;
import kh.edu.istad.ite.shared.enums.ImportTargetType;
import kh.edu.istad.ite.shared.enums.ItemType;
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
 * One attempt at migrating data in — one file, from upload to report.
 *
 * The uploaded file itself is not here. It lives in object storage and this
 * holds only the key, because a shop's whole price list does not belong in a
 * column, and because the file has to be readable again later: checking is
 * retried by re-reading it rather than by asking the user to upload twice.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "import_jobs",
        indexes = {
                @Index(name = "idx_import_jobs_business", columnList = "business_owner_id, created_date")
        }
)
public class ImportJob extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Guards the two transitions that must never happen twice.
     *
     * Committing an import is not reversible, and a second click while the
     * first is still in flight would import the same file again. The status
     * moves are made as conditional updates and this is what makes them fail
     * rather than interleave.
     */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "business_owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_import_jobs_business")
    )
    private Business business;

    /** Who started it, kept so the history says whose import this was. */
    @Column(name = "started_by_user_id", nullable = false, updatable = false)
    private UUID startedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private ImportSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private ImportTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ImportStatus status = ImportStatus.UPLOADED;

    @Column(name = "source_file_name", nullable = false, length = 255)
    private String sourceFileName;

    @Column(name = "source_file_size", nullable = false)
    private Long sourceFileSize;

    /** Where the upload sits in object storage. Never sent to a client. */
    @Column(name = "storage_object_key", nullable = false, length = 512)
    private String storageObjectKey;

    /**
     * The column headings found in the file, in the order they appear.
     *
     * Read once on upload so the matching screen has something to show before
     * anything else has been done with the file.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_columns", columnDefinition = "jsonb")
    private List<String> sourceColumns = new ArrayList<>();

    /** The first few rows, so the user can see their matching is right. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sample_rows", columnDefinition = "jsonb")
    private List<Map<String, String>> sampleRows = new ArrayList<>();

    /**
     * Which column feeds which FluxiBiz field, keyed by column heading.
     *
     * Stored as a document rather than rows of its own because it is only ever
     * read and written whole. It is also the shape a saved template would
     * take, which is why the choices that go with it — the fallbacks below —
     * sit beside it rather than being folded into it.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mappings", columnDefinition = "jsonb")
    private Map<String, String> columnMappings = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_strategy", nullable = false, length = 40)
    private ImportDuplicateStrategy duplicateStrategy = ImportDuplicateStrategy.SKIP;

    /**
     * The unit to count every imported item in when the file has no column for
     * it — which most exports do not.
     *
     * An item cannot be stocked without one, and guessing a unit from a name
     * would invent conversions nobody asked for, so the user picks one for the
     * whole file instead.
     */
    @Column(name = "default_unit_id")
    private UUID defaultUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_item_type", length = 20)
    private ItemType defaultItemType;

    @Column(name = "total_rows")
    private Integer totalRows = 0;

    @Column(name = "valid_rows")
    private Integer validRows = 0;

    @Column(name = "invalid_rows")
    private Integer invalidRows = 0;

    @Column(name = "duplicate_rows")
    private Integer duplicateRows = 0;

    @Column(name = "created_rows")
    private Integer createdRows = 0;

    @Column(name = "updated_rows")
    private Integer updatedRows = 0;

    @Column(name = "skipped_rows")
    private Integer skippedRows = 0;

    @Column(name = "failed_rows")
    private Integer failedRows = 0;

    /**
     * Rows that would put a quantity on a shelf, counted while checking.
     *
     * Worked out once, when every row is in front of us anyway, so the preview
     * can promise it without walking the staged rows a second time.
     */
    @Column(name = "opening_stock_rows")
    private Integer openingStockRows = 0;

    /**
     * How many things this file would actually bring into being.
     *
     * Not the same as the number of valid rows once a file lists one row per
     * option: five rows describing one shirt in five sizes create one item, and
     * a preview promising five would be a lie the shop only discovers
     * afterwards.
     */
    @Column(name = "entities_to_create")
    private Integer entitiesToCreate = 0;

    /** Item groups conjured from a category column that named ones we lacked. */
    @Column(name = "created_item_groups")
    private Integer createdItemGroups = 0;

    /** Opening balances posted alongside imported items. */
    @Column(name = "created_stock_entries")
    private Integer createdStockEntries = 0;

    @Column(name = "validation_started_at")
    private LocalDateTime validationStartedAt;

    @Column(name = "validation_completed_at")
    private LocalDateTime validationCompletedAt;

    @Column(name = "commit_started_at")
    private LocalDateTime commitStartedAt;

    @Column(name = "commit_completed_at")
    private LocalDateTime commitCompletedAt;

    /** Why the import as a whole stopped — not why any single row did. */
    @Column(name = "failure_message", length = 1000)
    private String failureMessage;
}
