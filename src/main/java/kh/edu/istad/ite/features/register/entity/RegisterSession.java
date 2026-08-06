package kh.edu.istad.ite.features.register.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.shared.enums.SessionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "register_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterSession extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_id", nullable = false)
    private CashRegister register;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private Instant openedAt;

    private Instant closedAt;

    /**
     * The currency this till is counted in, fixed when it opens. Cash is
     * physical, so a later base-currency change must not restate these
     * balances — it reads back in the currency it was actually counted in.
     */
    @Column(length = 10)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBalance;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal actualAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal differenceAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.OPEN;

    private String note;

    @ElementCollection
    @CollectionTable(
            name = "register_session_participants",
            joinColumns = @JoinColumn(name = "session_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "user_id"})
    )
    @Column(name = "user_id", nullable = false)
    private java.util.Set<String> participants = new java.util.HashSet<>();
}
