package kh.edu.istad.ite.features.order.entity;

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
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An add-on chosen on one order line — extra pearls on this latte.
 *
 * It rides along with the line rather than being a line of its own: it is
 * never sold alone, and its quantity follows whatever the line is.
 *
 * The name, price and usage are copied here as they stood when it was rung up.
 * A receipt from last month has to keep meaning what it meant, whatever the
 * add-on costs or consumes today.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "order_item_add_ons")
public class OrderItemAddOn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_item_add_ons_line")
    )
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "add_on_id",
            foreignKey = @ForeignKey(name = "fk_order_item_add_ons_add_on")
    )
    private AddOn addOn;

    @Column(name = "add_on_name", nullable = false, length = 150)
    private String addOnName;

    /** What one selection cost, per unit of the line. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /** Base units of the add-on one selection consumes. */
    @Column(name = "use_per_order", nullable = false, precision = 12, scale = 3)
    private BigDecimal usePerOrder = BigDecimal.ONE;

    /**
     * What this extra cost, batch by batch, when it left the shelf.
     *
     * The price of an add-on already rides on the line it garnished, so its
     * cost has to as well or the line reports a margin it never made. Taken
     * from the movement that consumed it rather than from what the add-on
     * costs today — the same rule the item's own cost follows.
     *
     * Zero on a line sold before add-on cost was counted. Those sales recorded
     * no add-on cost in their totals either, so the books still agree with
     * themselves; they simply both understate what those extras cost.
     */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;
}
