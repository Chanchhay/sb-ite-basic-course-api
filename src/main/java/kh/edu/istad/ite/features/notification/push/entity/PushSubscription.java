package kh.edu.istad.ite.features.notification.push.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One device's Web Push registration.
 *
 * The dashboard used to keep these in a JSON file on its own server —
 * workable on a single long-running Node process, but silently empty on a
 * serverless deployment, where each invocation can land on a different
 * instance with its own disk. Moving them here puts them next to the one
 * thing that already survives that: the backend's own database, which is
 * also where {@code PushNotificationClient} already turns for "who is this
 * owner" — this is just the other half of that same question, "how do I
 * reach them".
 *
 * `endpoint` is unique on its own, not paired with `userId`: it names one
 * browser's one subscription to one push service, so it is already the
 * right key for "the same device registering again" regardless of who it is
 * this time — a logout/login on a shared kiosk moves the row to the new
 * user rather than leaving a duplicate for the old one.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "push_subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_push_subscriptions_endpoint", columnNames = {"endpoint"})
        }
)
public class PushSubscription extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** The Keycloak subject this device notifies — never the business id. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    @Column(name = "expiration_time")
    private Long expirationTime;
}
