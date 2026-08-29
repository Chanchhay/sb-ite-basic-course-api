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
import kh.edu.istad.ite.shared.enums.MigrationJoinType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * How two of a migration's files line up.
 *
 * Deliberately only equality between one column and one column. Anything
 * richer — expressions, several keys at once, fuzzy matching on names — sounds
 * more capable and is mostly a way to join the wrong records confidently. A
 * shop discovering afterwards that their stock landed on the wrong items has
 * no way back except starting again.
 *
 * Approved by an operator, never inferred and applied. What we suggest is a
 * suggestion; this row records that somebody looked at the match counts and
 * agreed.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "migration_source_relationships",
        indexes = {
                @Index(name = "idx_migration_relationships_migration", columnList = "migration_id")
        }
)
public class MigrationSourceRelationship extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "migration_id", nullable = false)
    private AssistedMigration migration;

    /** The source being enriched — in practice the one that becomes items. */
    @Column(name = "left_source_id", nullable = false)
    private UUID leftSourceId;

    @Column(name = "left_column", nullable = false, length = 255)
    private String leftColumn;

    /** The source supplying the missing values. */
    @Column(name = "right_source_id", nullable = false)
    private UUID rightSourceId;

    @Column(name = "right_column", nullable = false, length = 255)
    private String rightColumn;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_type", nullable = false, length = 20)
    private MigrationJoinType joinType = MigrationJoinType.LEFT;
}
