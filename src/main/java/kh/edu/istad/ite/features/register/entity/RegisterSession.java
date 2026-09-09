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


    @Column(length = 10)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "base_opening_balance", precision = 12, scale = 2)
    private BigDecimal baseOpeningBalance;

    @Column(name = "secondary_currency", length = 10)
    private String secondaryCurrency;

    @Column(name = "secondary_opening_balance", precision = 14, scale = 2)
    private BigDecimal secondaryOpeningBalance;

    @Column(name = "secondary_exchange_rate", precision = 20, scale = 8)
    private BigDecimal secondaryExchangeRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "base_actual_amount", precision = 12, scale = 2)
    private BigDecimal baseActualAmount;

    @Column(name = "secondary_actual_amount", precision = 14, scale = 2)
    private BigDecimal secondaryActualAmount;

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
