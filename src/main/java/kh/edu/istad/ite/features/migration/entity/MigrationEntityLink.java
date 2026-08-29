package kh.edu.istad.ite.features.migration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.shared.enums.MigrationEntityType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * What a record in the old system became in FluxiBiz.
 *
 * Written after a successful import, and useless until much later — which is
 * exactly why it has to be written now. The day a shop asks to keep both
 * systems running for a fortnight, the question becomes "P001 changed, which
 * item is that?", and the only honest answer comes from having recorded it at
 * the moment we knew.
 *
 * Reconstructing it afterwards means matching on names and SKUs, which is the
 * guessing this whole feature exists to avoid.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "migration_entity_links",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_migration_links_source",
                        columnNames = {"business_owner_id", "source_system", "source_entity_type", "source_entity_id"}
                )
        },
        indexes = {
                @Index(name = "idx_migration_links_target", columnList = "fluxibiz_entity_type, fluxibiz_entity_id")
        }
)
public class MigrationEntityLink extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "business_owner_id", nullable = false)
    private UUID businessId;

    @Column(name = "migration_id")
    private UUID migrationId;

    /** Whose system it came from — "OLDPOS_X". Free text: we do not know theirs. */
    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @Column(name = "source_entity_type", length = 60)
    private String sourceEntityType;

    /**
     * Their identifier, as they wrote it.
     *
     * Absent when the customer's file genuinely had none — plenty do — and that
     * is recorded as absence rather than invented, because a made-up identifier
     * would match the wrong thing later with complete confidence.
     */
    @Column(name = "source_entity_id", length = 255)
    private String sourceEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fluxibiz_entity_type", nullable = false, length = 40)
    private MigrationEntityType fluxibizEntityType;

    @Column(name = "fluxibiz_entity_id", nullable = false)
    private UUID fluxibizEntityId;
}
