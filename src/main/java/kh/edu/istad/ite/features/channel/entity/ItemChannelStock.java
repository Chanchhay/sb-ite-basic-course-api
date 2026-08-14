package kh.edu.istad.ite.features.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How much of one item's stock one channel may sell.
 *
 * Not stock of its own — no balance is held here and nothing is deducted from
 * it by the ledger. It is a ceiling: the sale still comes off the one shelf,
 * and this only says how many of those units this channel was allowed.
 *
 * <p>Keyed by option as well as channel because stock is counted per option. A
 * single number against the channel could not say that Web sells the Large
 * ones only. {@code variant} is null on an item with no options — the item as
 * a whole is then the only thing there is a balance of.
 *
 * <p>A missing row is not an allocation of zero, in the same way a missing
 * {@link ItemChannelPrice} is not a price of zero: under {@code SHARED} it
 * means the channel sells from everything on hand. Only under
 * {@code ALLOCATED} does its absence stop a channel selling.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "item_channel_stocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_item_channel_stock",
                columnNames = {"item_id", "sales_channel_id", "variant_id"}
        )
)
public class ItemChannelStock extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_channel_id", nullable = false)
    private SalesChannel salesChannel;

    /** The option this allocation is of. Null on an item sold as itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ItemVariant variant;

    /** How many base units of the shelf this channel may sell. */
    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    /**
     * How many of them it has already sold.
     *
     * An allocation is consumed by selling: giving Web ten and letting it sell
     * ten again after every restock would be a promise the shelf never made.
     * Raised by the checkout that settles the order, never by the back office.
     */
    @Column(name = "sold_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal soldQuantity = BigDecimal.ZERO;

    /** What is left of this allocation, never below zero. */
    public BigDecimal remaining() {
        BigDecimal allocated = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal sold = soldQuantity == null ? BigDecimal.ZERO : soldQuantity;

        return allocated.subtract(sold).max(BigDecimal.ZERO);
    }
}
