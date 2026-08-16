package kh.edu.istad.ite.features.catalog.entity;

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
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.AttributeType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable list of option values — "Size: Small, Medium, Large" saved once so
 * it is not retyped on every item.
 *
 * A preset is a starting point, not a live link: applying it copies the values
 * onto the item. Editing the preset later does not reach back and rewrite items
 * already using it, because per-item tweaks are certain — not every drink comes
 * in Large — and a live link would silently mutate hundreds of items nobody
 * reviewed. That is why nothing here points back at an item.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "option_presets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_option_presets_business_name",
                columnNames = {"business_owner_id", "name"}
        )
)
public class OptionPreset extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 150)
    private String name;

    /** Mirrors the item attribute types a preset can fill in. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeType type = AttributeType.SELECTION;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean required = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<OptionPresetValue> values = new ArrayList<>();
}
