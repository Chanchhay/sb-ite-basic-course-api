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
import kh.edu.istad.ite.shared.enums.ImportSourceType;
import kh.edu.istad.ite.shared.enums.MigrationSourcePurpose;
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
 * One file the customer sent, within a migration that may have several.
 *
 * The reason this exists is that a shop's data is rarely in one place. Their
 * old system exports a product list; the stock count came off a handheld
 * scanner that knows only codes and quantities; the prices live in a
 * spreadsheet somebody maintains by hand. Each file is missing something the
 * others have, and demanding one complete file is demanding the customer do
 * the joining — which is exactly the work they are paying us to do.
 *
 * So the mapping lives here rather than on the migration. Two exports from the
 * same system will both have a column called {@code product_code}, and a
 * mapping keyed by heading alone could not tell which file's it meant.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "assisted_migration_sources",
        indexes = {
                @Index(name = "idx_migration_sources_migration", columnList = "migration_id, ordinal")
        }
)
public class AssistedMigrationSource extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "migration_id", nullable = false)
    private AssistedMigration migration;

    /**
     * Where this file sits in the list, and which one is the main one.
     *
     * The first source is the one whose records become items — the others
     * enrich it. Making that positional rather than a flag means there is
     * always exactly one, which a flag could not promise.
     */
    @Column(name = "ordinal", nullable = false)
    private Integer ordinal = 0;

    /**
     * What this file describes.
     *
     * Suggested from the headings and confirmed by the operator. It steers the
     * join suggestions and nothing else — a file whose purpose is wrong makes
     * for worse suggestions, not for wrong data.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private MigrationSourcePurpose purpose = MigrationSourcePurpose.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 40)
    private ImportSourceType sourceType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    /** Where the file is kept, exactly as the customer sent it. Never rewritten. */
    @Column(name = "raw_object_key", length = 500)
    private String rawObjectKey;

    /** Which sheet of a workbook the rows were read from. */
    @Column(name = "sheet_name", length = 255)
    private String sheetName;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "column_count")
    private Integer columnCount;

    /** The headings as they appeared, in order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_columns", columnDefinition = "jsonb")
    private List<String> sourceColumns = new ArrayList<>();

    /** This file's headings, pointed at FluxiBiz fields, as the operator settled it. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mappings", columnDefinition = "jsonb")
    private Map<String, String> columnMappings = new LinkedHashMap<>();

    /** Whether the columns have been read and profiled since the file arrived. */
    @Column(name = "analyzed", nullable = false)
    private boolean analyzed = false;

    /** Whether this file's records are the ones that become items. */
    public boolean isPrimary() {
        return ordinal != null && ordinal == 0;
    }
}
