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
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.features.dataimport.validation.RowIssue;
import kh.edu.istad.ite.shared.enums.ImportRowStatus;
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
 * One row of the uploaded file, held apart from the catalogue until the shop
 * says go.
 *
 * This is what makes an import something a shop can look at before it happens:
 * every row is read, matched, and checked here, and nothing reaches an item or
 * a stock ledger until the commit runs. It is also what makes checking
 * retryable and errors reportable per row.
 *
 * Both the row as it arrived and the row as FluxiBiz understood it are kept.
 * The first is what the user recognises when something looks wrong; the second
 * is what was actually judged, and without it a validation message can only be
 * taken on trust.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "import_rows",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_import_rows_job_row_number",
                        columnNames = {"import_job_id", "row_number"}
                )
        },
        indexes = {
                @Index(name = "idx_import_rows_job_status", columnList = "import_job_id, status")
        }
)
public class ImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "import_job_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_import_rows_job")
    )
    private ImportJob importJob;

    /**
     * The line the user would count to in their spreadsheet, headers included.
     *
     * Not the index of this row among the ones we kept: blank lines are
     * skipped, and a message about "row 40" has to point at row 40 of the file
     * they are looking at.
     */
    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, String> rawData = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_data", columnDefinition = "jsonb")
    private Map<String, Object> normalizedData = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ImportRowStatus status = ImportRowStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<RowIssue> issues = new ArrayList<>();

    /**
     * The identifier this row carried in the system it came from — a SKU, a
     * barcode, an old item code.
     *
     * What a re-import matches on, and what lets someone answer "where did
     * this item come from" a year later.
     */
    @Column(name = "external_id", length = 200)
    private String externalId;

    /** What this row became once committed. Null until then. */
    @Column(name = "committed_entity_id")
    private UUID committedEntityId;

    /** Set when the row also posted an opening balance. */
    @Column(name = "committed_stock_entry_id")
    private UUID committedStockEntryId;

    public boolean isCommitted() {
        return status == ImportRowStatus.CREATED || status == ImportRowStatus.UPDATED;
    }
}
