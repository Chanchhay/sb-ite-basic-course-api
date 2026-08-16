package kh.edu.istad.ite.features.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How much one outgoing movement took from one layer, and at what cost.
 *
 * A single sale often spans two deliveries — the last four of the old batch and
 * six of the new. Without this row the cost of that sale is a number nobody can
 * take apart afterwards.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "stock_consumptions")
public class StockConsumption extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "stock_entry_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_consumptions_entry")
    )
    private StockEntry stockEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "stock_layer_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_consumptions_layer")
    )
    private StockLayer stockLayer;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    /** Copied from the layer, so a later correction cannot rewrite history. */
    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost;
}
