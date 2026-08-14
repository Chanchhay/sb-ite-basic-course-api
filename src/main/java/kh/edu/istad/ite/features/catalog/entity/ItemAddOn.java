package kh.edu.istad.ite.features.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * An add-on offered on one item, and whether it is currently sold there.
 *
 * Being attached and being on sale are two different things. A shop that has
 * run out of pearls, or that stops offering them on one drink for a while,
 * wants them off the menu for that item — not unlinked, which would lose the
 * fact that this drink offers pearls at all and have to be set up again.
 *
 * Keyed by the two columns the join table already had, so it maps onto the
 * existing {@code item_add_ons} rows rather than needing them rewritten under
 * a new surrogate id.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "item_add_ons")
public class ItemAddOn {

    @EmbeddedId
    private ItemAddOnId id = new ItemAddOnId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("itemId")
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("addOnId")
    @JoinColumn(name = "add_on_id", nullable = false)
    private AddOn addOn;

    /**
     * Whether this item currently offers it for sale.
     *
     * Defaults to true: attaching an add-on to an item is saying you sell it
     * there. Existing rows predate the column and are read the same way.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean available = true;

    public ItemAddOn(Item item, AddOn addOn) {
        this.item = item;
        this.addOn = addOn;
        this.id = new ItemAddOnId(item.getId(), addOn.getId());
    }

    /** Null-safe: a row written before the column existed is on sale. */
    public boolean isAvailable() {
        return available == null || available;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class ItemAddOnId implements Serializable {

        @Column(name = "item_id")
        private UUID itemId;

        @Column(name = "add_on_id")
        private UUID addOnId;
    }
}
