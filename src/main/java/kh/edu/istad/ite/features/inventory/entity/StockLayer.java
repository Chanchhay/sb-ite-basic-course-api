package kh.edu.istad.ite.features.inventory.entity;

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
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.AddOn;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One batch of stock that arrived at a known cost.
 *
 * Stock is valued first-in-first-out, which only works if each arrival keeps
 * its own price: two deliveries of the same item a month apart rarely cost the
 * same, and what leaves today should be costed at what it was actually bought
 * for, oldest first. A running average would smear that into one number and
 * lose the link to the delivery it came from.
 *
 * {@code quantityRemaining} counts down as stock leaves. A layer at zero is
 * kept rather than deleted — it is the audit trail behind every movement that
 * consumed it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "stock_layers")
public class StockLayer extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "business_owner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_layers_business")
    )
    private Business business;

    /** Exactly one of {@code item} and {@code addOn} is set, as on the entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "item_id",
            foreignKey = @ForeignKey(name = "fk_stock_layers_item")
    )
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "add_on_id",
            foreignKey = @ForeignKey(name = "fk_stock_layers_add_on")
    )
    private AddOn addOn;

    /**
     * Which option this batch arrived for, matching its entry.
     *
     * Batches are per option as well, or a sale of Large would be costed at
     * what Small was bought for — the two are ordered separately and rarely
     * cost the same.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "variant_id",
            foreignKey = @ForeignKey(name = "fk_stock_layers_variant")
    )
    private ItemVariant variant;

    /** The movement that brought it in. Null on a layer opened by backfill. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "source_entry_id",
            foreignKey = @ForeignKey(name = "fk_stock_layers_source_entry")
    )
    private StockEntry sourceEntry;

    /** Cost of one base unit in this batch. */
    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "quantity_received", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityReceived;

    @Column(name = "quantity_remaining", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityRemaining;

    /** What FIFO orders by, so a backdated delivery still queues correctly. */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** Lot and expiry as recorded on the way in, carried for traceability. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "batch_data", columnDefinition = "jsonb")
    private Map<String, Object> batchData;
}
