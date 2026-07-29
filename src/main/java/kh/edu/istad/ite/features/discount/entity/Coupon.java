package kh.edu.istad.ite.features.discount.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import kh.edu.istad.ite.features.business.entity.Business;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id")
    private Discount discountId;
    @Column(name = "code")
    private String code;
    @Column(name = "usage_limit")
    private Integer usageLimit;
    @Column(name = "usage_limit_per_customer")
    private Integer usageLimitPerCustomer;
    @Column(name = "used_count")
    private Integer usedCount;
    @Column(name = "min_purchase_amount")
    private Double minPurchaseAmount;
    @Column(name = "starts_at")
    private LocalDateTime startsAt;
    @Column(name = "ends_at")
    private LocalDateTime endsAt;
    @Column(name = "status")
    private String status;
    @Column(name = "created_by")
    private BigInteger createdBy;
}
