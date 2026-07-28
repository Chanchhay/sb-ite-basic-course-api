package kh.edu.istad.ite.features.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "platform_feature_flags")
public class PlatformFeatureFlag extends BasedAuditingEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private BusinessFeature feature;

    @Column(nullable = false)
    private Boolean enabled = true;

    /// Shown next to the switch, so the next admin learns why rather than guessing.
    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    @Column(name = "disabled_by")
    private UUID disabledBy;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;
}
