package kh.edu.istad.ite.features.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An extra piled on top of an item — pearls, an extra shot.
 *
 * It belongs to the business, not to the item: "Extra shot" is defined once and
 * attached to every drink that offers it, so a change to it is a change
 * everywhere. Items link to it through {@code item_add_ons}.
 *
 * Its price is one number for the whole business: "Extra shot" costs the same
 * wherever it is offered, which is the standard a shop advertises. Pricing is
 * set in Sale Management, the same as it is for an item.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "add_ons",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_add_ons_business_slug",
                        columnNames = {"business_owner_id", "slug"}
                ),
                @UniqueConstraint(
                        name = "uk_add_ons_business_name",
                        columnNames = {"business_owner_id", "name"}
                )
        }
)
public class AddOn extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 200)
    private String slug;

    /** The smallest quantity this is used in. {@code usePerOrder} is in it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_unit_id")
    private Unit baseUnit;

    /**
     * How much one selection takes off, in base units — 1 for a shot, 30 g for
     * a scoop of pearls.
     */
    @Column(name = "use_per_order", nullable = false, precision = 12, scale = 3)
    private BigDecimal usePerOrder = BigDecimal.ONE;

    /** Larger units it is bought in, each expressed in base units. */
    @OneToMany(mappedBy = "addOn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AddOnUomConversion> uomConversions = new ArrayList<>();

    /**
     * What one selection costs the customer, anywhere it is offered.
     *
     * Null means it has never been priced and cannot be sold yet — the till
     * says so rather than adding it for nothing.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 255)
    private String note;
}
