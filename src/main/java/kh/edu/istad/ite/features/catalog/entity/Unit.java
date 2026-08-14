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
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.UnitCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A unit of measure.
 *
 * A null {@code business} marks a platform unit: shared by everyone, selectable
 * everywhere, and never edited or deleted by a business owner. A set one is
 * that business's own — "Sack", "Crate" — visible only to it.
 *
 * Conversions are deliberately not here. How many grams are in a sack is true
 * of an item, not of the unit: a sack of rice and a sack of flour are both
 * sacks and do not weigh the same. See {@link ItemUomConversion}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "units")
public class Unit extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Null for a platform unit. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id")
    private Business business;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 250)
    private String slug;

    /** Short form shown beside amounts — "g", "ml", "pc". */
    @Column(length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UnitCategory category = UnitCategory.COUNT;

    @Column(length = 255)
    private String note;

    /** A platform unit belongs to no business, so nobody may edit it. */
    public boolean isSystem() {
        return business == null;
    }
}
