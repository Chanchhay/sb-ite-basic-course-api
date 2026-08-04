package kh.edu.istad.ite.features.notification.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import kh.edu.istad.ite.features.business.entity.Business;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification_senders")
public class NotificationSender extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;                          // tenant id

    @Column(name = "sender_id", nullable = false, length = 100)
    private String senderId;                      // Keycloak subject / actor id

    @Column(name = "sender_name", length = 100)
    private String senderName;                    // Sender username / display name

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "receiver_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> receiverIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "deep_link", columnDefinition = "text")
    private String deepLink;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

//    @Column(name = "created_at", nullable = false)
//    private LocalDateTime createdAt;

    public static NotificationSender create(UUID userId, String senderId, String senderName, List<String> receiverIds,
                                            NotificationType type, String title,
                                            String content, String deepLink) {
        NotificationSender s = new NotificationSender();
        s.userId = userId;
        s.senderId = senderId;
        s.senderName = senderName;
        s.receiverIds = new ArrayList<>(receiverIds);
        s.type = type;
        s.title = title;
        s.content = content;
        s.deepLink = deepLink;
        s.deleted = false;
        return s;
    }
}
