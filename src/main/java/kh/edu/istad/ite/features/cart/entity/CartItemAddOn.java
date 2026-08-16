package kh.edu.istad.ite.features.cart.entity;

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
 * An add-on ticked on a basket line — extra pearls on this latte.
 *
 * It rides along with the line rather than being a line of its own: it is
 * never sold alone, and its quantity follows whatever the line is.
 *
 * The name, price and usage are copied here as they stood when it was ticked,
 * for the same reason the price of the line itself is snapshotted — a basket
 * left open overnight must still cost what it said it cost. The order side
 * keeps its own copy again, since the basket is emptied once it settles; see
 * {@link kh.edu.istad.ite.features.order.entity.OrderItemAddOn}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "cart_item_add_ons")
public class CartItemAddOn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "cart_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cart_item_add_ons_line")
    )
    private CartItem cartItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "add_on_id",
            foreignKey = @ForeignKey(name = "fk_cart_item_add_ons_add_on")
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
}
