package kh.edu.istad.ite.features.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.entity.Unit;
import kh.edu.istad.ite.shared.enums.PriceOverrideKind;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One channel's exception to the business price, for one thing it sells.
 *
 * The line is identified exactly as Set Price identifies it — the item on its
 * own, one of its options, or one of its larger units — because a shop that
 * charges more for delivery rarely means "everything, by the same amount", and
 * a case is not marked up like a single can.
 *
 * A missing row is not a price of zero: it means this channel has no exception
 * and charges what the business charges. That is why "reset to base" is a
 * delete rather than a guess at what the number used to be.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "item_channel_prices")
public class ItemChannelPrice extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_channel_id", nullable = false)
    private SalesChannel salesChannel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** The option this applies to. Null on an item sold as itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ItemVariant variant;

    /** The larger unit this applies to. Null for the single. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_kind", nullable = false, length = 20)
    private PriceOverrideKind overrideKind = PriceOverrideKind.INHERIT;

    @Column(name = "override_value", precision = 12, scale = 4)
    private BigDecimal overrideValue;

    /** How this line is told apart within its channel. */
    public String lineKey() {
        return item.getId()
                + ":" + (variant == null ? "" : variant.getId())
                + ":" + (unit == null ? "" : unit.getId());
    }

    public static String lineKey(UUID itemId, UUID variantId, UUID unitId) {
        return itemId
                + ":" + (variantId == null ? "" : variantId)
                + ":" + (unitId == null ? "" : unitId);
    }
}
