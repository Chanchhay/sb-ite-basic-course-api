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
 * How many of an add-on's base units make up one of a larger unit — "1 bag =
 * 3000 g of pearls".
 *
 * The same reasoning as {@link ItemUomConversion}: this is true of the add-on,
 * not of the unit. One supplier's bag of pearls and another's are both bags and
 * do not hold the same. {@code factor} always reads as base units per one of
 * {@code unit}, never the inverse.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "add_on_uom_conversions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_add_on_uom_conversions_add_on_unit",
                columnNames = {"add_on_id", "unit_id"}
        )
)
public class AddOnUomConversion extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "add_on_id", nullable = false)
    private AddOn addOn;

    /** The larger unit. Never the add-on's base unit — that factor is 1. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal factor;
}
