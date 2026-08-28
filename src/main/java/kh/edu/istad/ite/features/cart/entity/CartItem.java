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

    @Column(name = "base_price", precision = 10, scale = 2)
    private BigDecimal basePrice;

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


    @Builder.Default
    @OneToMany(mappedBy = "cartItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItemSelection> selections = new ArrayList<>();

    public void addSelection(CartItemSelection selection) {
        selection.setCartItem(this);
        selections.add(selection);
    }


    @Builder.Default
    @OneToMany(mappedBy = "cartItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItemAddOn> addOns = new ArrayList<>();

    public void addAddOn(CartItemAddOn addOn) {
        addOn.setCartItem(this);
        addOns.add(addOn);
    }

    /** What the extras add to one of this line. */
    public BigDecimal addOnsPerUnit() {
        if (addOns == null || addOns.isEmpty()) return BigDecimal.ZERO;

        return addOns.stream()
                .map(CartItemAddOn::getUnitPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What one of this line costs with its extras on top.
     *
     * The snapshot stays the price of the thing itself, so a basket can still
     * show what the drink costs beside what was added to it.
     */
    public BigDecimal priceWithAddOns() {
        BigDecimal base = priceSnapshot == null ? BigDecimal.ZERO : priceSnapshot;

        return base.add(addOnsPerUnit());
    }

    /**
     * The extras reduced to one comparable string, sorted so the same two
     * ticked in a different order still meet on the same line.
     */
    public String addOnKey() {
        if (addOns == null || addOns.isEmpty()) return "";

        return addOns.stream()
                .map(addOn -> addOn.getAddOn() == null
                        ? addOn.getAddOnName()
                        : addOn.getAddOn().getId().toString())
                .sorted()
                .collect(Collectors.joining("|"));
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

    /** What the line comes to — the extras included, since they are billed. */
    @Transient
    public BigDecimal getSubtotal() {
        if (priceSnapshot == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return priceWithAddOns().multiply(BigDecimal.valueOf(quantity));
    }
}