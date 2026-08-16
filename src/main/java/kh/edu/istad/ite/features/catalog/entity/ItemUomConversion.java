package kh.edu.istad.ite.features.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How many of an item's base units make up one of a larger unit — "1 carton =
 * 6 bottles", "1 sack = 25000 g".
 *
 * This belongs to the item rather than to the unit because it is only true of
 * the item: a sack of rice and a sack of flour are both sacks and do not weigh
 * the same. It also means packaging needs no separate concept — "sold by the
 * bottle, holds 750 ml" is a conversion on an item based in millilitres.
 *
 * {@code factor} always reads as base units per one of {@code unit}, never the
 * inverse. That is the direction people get wrong, so only one is stored.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "item_uom_conversions",
        // One row per option per unit: the same case can be defined for Large
        // and for Small, and they are different things.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_item_uom_conversions_item_unit_variant",
                columnNames = {"item_id", "unit_id", "variant_id"}
        )
)
public class ItemUomConversion extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /**
     * Which option this larger unit is for.
     *
     * A shop that sells Large by the case need not sell Small that way, and
     * the two need not hold the same number — so the option is part of what a
     * conversion is, not a label on it. Null on an item with no options.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ItemVariant variant;

    /** The larger unit. Never the item's base unit — that factor is always 1. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal factor;

    /**
     * What one of this unit sells for — a case, a six-pack.
     *
     * Null means the item is not sold in this unit; it is only ever bought and
     * counted in it. Stock is still held in base units either way: selling one
     * case takes {@code factor} of them off the shelf.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal price;
}
