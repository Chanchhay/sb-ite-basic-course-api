package kh.edu.istad.ite.features.cart.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

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