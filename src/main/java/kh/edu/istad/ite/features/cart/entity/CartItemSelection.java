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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An option chosen on a basket line, carried until the line becomes an order.
 *
 * The order side keeps its own copy rather than pointing back here, since the
 * basket is emptied once it settles; see
 * {@link kh.edu.istad.ite.features.order.entity.OrderItemSelection}.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cart_item_selections")
public class CartItemSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "cart_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cart_item_selections_line")
    )
    private CartItem cartItem;

    @Column(name = "attribute_name", nullable = false, length = 150)
    private String attributeName;

    /** The stored value — the identity, and what two lines are compared on. */
    @Column(name = "value", nullable = false, length = 150)
    private String value;

    /** How it was shown to the shopper. Falls back to {@link #value}. */
    @Column(name = "label", length = 150)
    private String label;

    public String display() {
        return label == null || label.isBlank() ? value : label;
    }
}
