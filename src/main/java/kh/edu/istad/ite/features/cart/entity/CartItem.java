package kh.edu.istad.ite.features.cart.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "cart_items")
public class CartItem extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ItemVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;

    /** The unit this line is sold in — a case, a six-pack, or the base unit. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    /**
     * Base units one of {@link #unit} holds, as it stood when the line was
     * added. Snapshotted so redefining a case later cannot change what a
     * basket already agreed to.
     */
    @Column(name = "unit_factor", precision = 18, scale = 6)
    private BigDecimal unitFactor = BigDecimal.ONE;

    /**
     * The options chosen on this line — 50% sugar, no ice.
     *
     * Part of what makes the line the line: two of the same drink at different
     * sweetness are two orders, not one of quantity two, so
     * {@link #selectionKey()} joins the item and the variant in deciding
     * whether an add lands on an existing line or starts a new one.
     */
    @Builder.Default
    @OneToMany(mappedBy = "cartItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItemSelection> selections = new ArrayList<>();

    public void addSelection(CartItemSelection selection) {
        selection.setCartItem(this);
        selections.add(selection);
    }

    /**
     * A line's choices reduced to one comparable string.
     *
     * Sorted by attribute name so the same choices made in a different order
     * still meet on the same line.
     */
    public String selectionKey() {
        if (selections == null || selections.isEmpty()) return "";

        return selections.stream()
                .map(selection -> selection.getAttributeName() + "=" + selection.getValue())
                .sorted()
                .collect(Collectors.joining("|"));
    }

    /** Base units this line takes off the shelf. */
    public BigDecimal baseQuantity() {
        BigDecimal factor = unitFactor == null ? BigDecimal.ONE : unitFactor;

        return factor.multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }

    @Transient
    public BigDecimal getSubtotal() {
        if (priceSnapshot == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return priceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }
}