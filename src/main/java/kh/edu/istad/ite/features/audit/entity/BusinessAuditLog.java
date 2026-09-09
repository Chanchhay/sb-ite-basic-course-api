package kh.edu.istad.ite.features.audit.entity;

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
import kh.edu.istad.ite.shared.enums.BusinessAuditAction;
import kh.edu.istad.ite.shared.enums.BusinessAuditTarget;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One thing that happened inside one shop, and who did it.
 *
 * A separate table from {@code admin_audit_logs} rather than a business column
 * bolted onto it. That table is the platform's record of FluxiBiz staff acting
 * *on* businesses — nothing in it belongs to a shop, and a shop owner reading
 * it would see other people's shops. This one is scoped to a business from the
 * first column, so the tenant filter is a property of the table and not
 * something every query has to remember.
 *
 * Rows are written once and never amended; every column is `updatable = false`
 * so an audit trail that quietly changed under someone is not reachable
 * through the mapping.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "business_audit_logs",
        indexes = {
                @Index(name = "idx_business_audit_logs_business_created",
                        columnList = "business_id, created_at"),
                @Index(name = "idx_business_audit_logs_actor", columnList = "actor_id"),
                @Index(name = "idx_business_audit_logs_action", columnList = "action_type")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_business_audit_logs_session",
                columnNames = {"business_id", "session_id"})
)
public class BusinessAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 100)
    private String actorId;

    /**
     * Who the actor was at the time, spelled out.
     *
     * Stored rather than resolved on read: a name looked up when the log is
     * displayed is the name that person has *now*, and an audit entry that
     * renames itself after someone leaves is not a record of anything.
     */
    @Column(name = "actor_username", updatable = false, length = 150)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 60)
    private BusinessAuditAction actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private BusinessAuditTarget targetType;

    /** The staff member or role acted on. Free text: a Keycloak role id is not a UUID. */
    @Column(name = "target_id", updatable = false, length = 100)
    private String targetId;

    /** The target's name at the time; survives the target being deleted. */
    @Column(name = "target_label", updatable = false, length = 255)
    private String targetLabel;

    @Column(name = "previous_state", updatable = false, length = 255)
    private String previousState;

    @Column(name = "new_state", updatable = false, length = 255)
    private String newState;

    /**
     * The Keycloak session (`sid`) a sign-in belongs to, for sign-ins only.
     *
     * This is what makes "record a sign-in" idempotent: every authenticated
     * request carries it, and the unique constraint with `business_id` turns
     * the thousandth request of a session into a no-op rather than the
     * thousandth row. Null for everything that is not a sign-in — Postgres
     * treats nulls as distinct, so the constraint does not collapse them.
     */
    @Column(name = "session_id", updatable = false, length = 100)
    private String sessionId;

    @Column(name = "ip_address", updatable = false, length = 60)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
