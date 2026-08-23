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
