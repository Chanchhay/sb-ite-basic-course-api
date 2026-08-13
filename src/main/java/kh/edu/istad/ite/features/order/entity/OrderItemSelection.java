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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An option chosen on one order line — "Sugar Level: 50%".
 *
 * Distinct from both of the things it sits beside. A variant is a different
 * thing to sell, with its own price and its own shelf; an add-on is something
 * extra that costs money and consumes stock. A selection is neither: it changes
 * nothing about what is charged or what comes off the shelf, and only says how
 * the thing should be made. Which is exactly why it was possible to drop it for
 * so long without anything failing — the money still balanced, and only the
 * customer noticed, when their drink came out at full sugar.
 *
 * The name and value are copied here as text rather than pointed at the item's
 * attribute, for the same reason the add-on beside it copies its name and
 * price: a shop that renames "Sugar Level" to "Sweetness" next month must not
 * change what last month's ticket said.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "order_item_selections")
public class OrderItemSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_item_selections_line")
    )
    private OrderItem orderItem;

    /** The attribute's name as it read when the line was rung up. */
    @Column(name = "attribute_name", nullable = false, length = 150)
    private String attributeName;

    /** The stored value — the identity, and what reporting groups by. */
    @Column(name = "value", nullable = false, length = 150)
    private String value;

    /** How it was shown to the customer. Falls back to {@link #value}. */
    @Column(name = "label", length = 150)
    private String label;

    /** What a ticket, a receipt or a kitchen screen should print. */
    public String display() {
        return label == null || label.isBlank() ? value : label;
    }
}
