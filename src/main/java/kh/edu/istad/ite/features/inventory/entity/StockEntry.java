package kh.edu.istad.ite.features.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.shared.enums.StockEntryType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "stock_entries"
)
public class StockEntry extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "business_owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_entries_business")
    )
    private Business business;

    /**
     * What moved. Exactly one of {@code item} and {@code addOn} is set.
     *
     * An add-on is counted like an item — pearls run out the same way a cup of
     * milk does — but it is never sold on its own, so it lives in its own
     * library and needs its own column here rather than pretending to be an
     * item.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "item_id",
            foreignKey = @ForeignKey(name = "fk_stock_entries_item")
    )
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "add_on_id",
            foreignKey = @ForeignKey(name = "fk_stock_entries_add_on")
    )
    private AddOn addOn;

    /**
     * Which option of the item moved, when the item is sold in options.
     *
     * An option is a stockable thing in its own right — it already carries its
     * own SKU and barcode — so a shop that sells Small and Large counts them
     * apart rather than sharing one balance that answers neither question.
     *
     * Null means the movement is against the item as a whole: either it has no
     * options, or it is stock recorded before the item gained any. Those older
     * quantities stay where they are and are read as unassigned rather than
     * being guessed into an option nobody chose.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "variant_id",
            foreignKey = @ForeignKey(name = "fk_stock_entries_variant")
    )
    private ItemVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private StockEntryType entryType;

    @Column(name = "quantity_change", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityChange;

    @Column(name = "quantity_before", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityAfter;

    /**
     * On the way in: what one base unit was bought for.
     * On the way out: what one base unit actually cost, worked out from the
     * layers consumed — never typed by hand.
     */
    @Column(name = "unit_cost", precision = 18, scale = 2)
    private BigDecimal unitCost;

    /**
     * What the whole outgoing movement cost, summed across the layers it took
     * from. Null on the way in, where nothing has been consumed yet.
     */
    @Column(name = "cost_of_goods", precision = 18, scale = 2)
    private BigDecimal costOfGoods;

    /**
     * What one base unit was sold for, on a stock-out that is a sale made away
     * from the till. Revenue, never cost — the two used to share one field and
     * a typed sale price would silently become the item's cost.
     */
    @Column(name = "unit_sale_price", precision = 18, scale = 2)
    private BigDecimal unitSalePrice;

    /**
     * What the operator actually counted, in the unit they counted it in — "2
     * sacks", where {@code quantityChange} holds the 50000 g that reached the
     * shelf. Kept so the ledger reads back the way the movement happened.
     */
    @Column(name = "entered_quantity", precision = 18, scale = 3)
    private BigDecimal enteredQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "entered_unit_id",
            foreignKey = @ForeignKey(name = "fk_stock_entries_entered_unit")
    )
    private kh.edu.istad.ite.features.catalog.entity.Unit enteredUnit;

    /**
     * The lot and dates this movement was recorded against.
     *
     * The batch it opened carries the same three, and is what the queue is
     * actually ordered by. They are kept here too so the ledger reads back as
     * what was entered — a batch can be drawn down to nothing and stop being
     * worth listing, while the movement that brought it in stays.
     */
    @Column(name = "lot_number", length = 80)
    private String lotNumber;

    @Column(name = "manufactured_at")
    private LocalDate manufacturedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "batch_data", columnDefinition = "jsonb")
    private Map<String, Object> batchData;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_number", length = 80)
    private String referenceNumber;

    @Column(length = 255)
    private String reason;

    /** The id of whichever target this entry is against. */
    public UUID getTargetId() {
        return item != null ? item.getId() : (addOn == null ? null : addOn.getId());
    }

    /**
     * What this entry keeps a running balance for.
     *
     * An item sold in options keeps one balance per option, so the id of the
     * item alone no longer identifies a balance — two entries on the same item
     * belong to different chains when they name different options.
     */
    public TargetKey getTargetKey() {
        return new TargetKey(
                item == null ? null : item.getId(),
                variant == null ? null : variant.getId(),
                addOn == null ? null : addOn.getId()
        );
    }

    /** One balance: an add-on, an item, or one option of an item. */
    public record TargetKey(UUID itemId, UUID variantId, UUID addOnId) {
    }
}
