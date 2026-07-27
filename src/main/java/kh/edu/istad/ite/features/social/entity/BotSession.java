package kh.edu.istad.ite.features.social.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.shared.enums.ChannelType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "bot_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bot_sessions_business_channel_external_id",
                columnNames = {"business_owner_id", "channel", "external_id"}
        )
)
public class BotSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id")
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ChannelType channel;

    // Telegram chat id (as string) - the address we send replies back to
    @Column(name = "external_id", nullable = false, length = 150)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "cart_id")
    private UUID cartId;

    // Simple conversation state machine - e.g. IDLE, BROWSING_CATALOG, AWAITING_CHECKOUT
    @Column(name = "state", length = 60)
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", columnDefinition = "jsonb")
    private Map<String, Object> context = new HashMap<>();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
