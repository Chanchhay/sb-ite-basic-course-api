package kh.edu.istad.ite.features.order.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ItemVariant variant;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    // Name of the catalog Discount that produced discountAmount for this
    // line, if any — so receipts/checkout can show which promo applied to
    // which item instead of just a lump order-level discount total.
    @Column(name = "discount_label", length = 150)
    private String discountLabel;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "line_number")
    private Integer lineNumber;

    /**
     * The unit this line is sold in: a case, a six-pack, or the item's own
     * base unit when nothing else was chosen.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    /**
     * How many base units one of {@link #unit} holds, as it stood when the
     * line was rung up.
     *
     * Snapshotted rather than read back from the item: a shop that redefines
     * its case from 24 to 12 must not change what last month's receipt meant,
     * or what it took off the shelf.
     */
    @Column(name = "unit_factor", precision = 18, scale = 6)
    private BigDecimal unitFactor = BigDecimal.ONE;

    /**
     * The extras chosen on this line — pearls on this latte.
     *
     * Each is priced per unit of the line, so two lattes with pearls charge
     * for pearls twice and take twice as much off the tub.
     */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemAddOn> addOns = new ArrayList<>();

    /** What the extras add to one unit of this line. */
    public BigDecimal addOnsPerUnit() {
        return addOns.stream()
                .map(OrderItemAddOn::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** One unit of this line, extras included — what it actually sells for. */
    public BigDecimal priceWithAddOns() {
        BigDecimal base = unitPrice == null ? BigDecimal.ZERO : unitPrice;

        return base.add(addOnsPerUnit());
    }

    public void addAddOn(OrderItemAddOn addOn) {
        addOn.setOrderItem(this);
        addOns.add(addOn);
    }

    /**
     * The options chosen on this line — 50% sugar, no ice.
     *
     * Costs nothing and consumes nothing, unlike the add-ons above; it is how
     * the thing is made, and the only place that instruction survives once the
     * basket is gone.
     */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemSelection> selections = new ArrayList<>();

    public void addSelection(OrderItemSelection selection) {
        selection.setOrderItem(this);
        selections.add(selection);
    }

    /** Base units this line moves: quantity times the factor. */
    public BigDecimal baseQuantity() {
        BigDecimal factor = unitFactor == null ? BigDecimal.ONE : unitFactor;

        return factor.multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }

}
