package kh.edu.istad.ite.features.notification.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import kh.edu.istad.ite.features.business.entity.Business;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification_receivers")
public class NotificationReceiver extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;                          // tenant id

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_sender_id", nullable = false)
    private NotificationSender notificationSender;

    @Column(name = "receiver_id", nullable = false, length = 100)
    private String receiverId;                    // Keycloak subject of the recipient

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public static NotificationReceiver create(UUID userId, NotificationSender sender, String receiverId) {
        NotificationReceiver r = new NotificationReceiver();
        r.userId = userId;
        r.notificationSender = sender;
        r.receiverId = receiverId;
        r.read = false;
        r.deleted = false;
        return r;
    }

    public void markRead(LocalDateTime when)      { if (!read) { read = true; readAt = when; } }
    public void softDelete()                { deleted = true; }
}
