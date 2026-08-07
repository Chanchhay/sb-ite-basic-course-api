package kh.edu.istad.ite.features.customer.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import kh.edu.istad.ite.features.business.entity.Business;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customers")
public class Customer extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "global_customer_id")
    private GlobalCustomer globalCustomer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_tier_id")
    private MembershipType membershipType;

    @Column(length = 100)
    private String address;

    @Column(name = "total_spend", precision = 14, scale = 2, nullable = false)
    private BigDecimal totalSpend = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_channel_id")
    private SalesChannel salesChannel;

    @Column(name = "became_membership_at")
    private LocalDateTime becameMembershipAt;

    @Column(name = "is_active")
    private Boolean active = true;
}
