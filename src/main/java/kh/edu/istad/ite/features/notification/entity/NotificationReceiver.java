package kh.edu.istad.ite.features.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.features.business.entity.Business;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification_receivers")
public class NotificationReceiver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_sender_id")
    private NotificationSender notificationSender;

    @Column(name = "receiver_id", nullable = false, length = 100)
    private String receiverId;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public static NotificationReceiver create(Business business, NotificationSender sender, String receiverId) {
        NotificationReceiver r = new NotificationReceiver();
        r.business = business;
        r.notificationSender = sender;
        r.receiverId = receiverId;
        r.read = false;
        r.deleted = false;
        return r;
    }

    public void markRead(LocalDateTime when) {
        if (!read) {
            read = true;
            readAt = when;
        }
    }

    public void softDelete() {
        deleted = true;
    }
}
