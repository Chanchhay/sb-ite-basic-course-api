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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.AddOnSelectionRule;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A group of add-ons offered together, with a rule for how many may be picked
 * — "Toppings: any number, optional", "Milk: exactly one, required".
 *
 * The set holds the rule; the add-ons themselves stay in the shared library, so
 * putting "Pearls" in two sets does not duplicate it or its stock.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "add_on_sets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_add_on_sets_business_name",
                columnNames = {"business_owner_id", "name"}
        )
)
public class AddOnSet extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddOnSelectionRule rule = AddOnSelectionRule.ANY;

    /** Only meaningful when the rule is {@code UP_TO}. */
    @Column(name = "max_choices")
    private Integer maxChoices;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean required = false;

    /** A link, never a cascade: removing a set must not delete its add-ons. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "add_on_set_items",
            joinColumns = @JoinColumn(name = "add_on_set_id"),
            inverseJoinColumns = @JoinColumn(name = "add_on_id")
    )
    private Set<AddOn> addOns = new LinkedHashSet<>();
}
