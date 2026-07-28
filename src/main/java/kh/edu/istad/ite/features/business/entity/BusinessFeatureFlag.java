package kh.edu.istad.ite.features.business.entity;

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
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
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
        name = "business_feature_flags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_business_feature_flags",
                columnNames = {"business_owner_id", "feature"}
        )
)
public class BusinessFeatureFlag extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BusinessFeature feature;

    @Column(nullable = false)
    private Boolean enabled = true;

    /// Shown to the shop owner, so they learn why rather than guessing.
    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    @Column(name = "disabled_by")
    private UUID disabledBy;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;
}
