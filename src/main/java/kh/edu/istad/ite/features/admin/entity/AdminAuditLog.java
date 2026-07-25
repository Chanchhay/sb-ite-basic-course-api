package kh.edu.istad.ite.features.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "admin_audit_logs",
        indexes = {
                @Index(name = "idx_admin_audit_logs_created_at", columnList = "created_at"),
                @Index(name = "idx_admin_audit_logs_target", columnList = "target_type, target_id"),
                @Index(name = "idx_admin_audit_logs_actor", columnList = "actor_id"),
                @Index(name = "idx_admin_audit_logs_action", columnList = "action_type")
        }
)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 100)
    private String actorId;

    @Column(name = "actor_username", updatable = false, length = 150)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 60)
    private AdminActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    /** Name of the target at the time of the action; survives target deletion. */
    @Column(name = "target_label", updatable = false, length = 255)
    private String targetLabel;

    @Column(name = "previous_state", updatable = false, length = 60)
    private String previousState;

    @Column(name = "new_state", updatable = false, length = 60)
    private String newState;

    @Column(name = "ip_address", updatable = false, length = 60)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
