package kh.edu.istad.ite.features.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.ChannelStockMode;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_items_business_slug",
                        columnNames = {"business_owner_id", "slug"}
                ),
                @UniqueConstraint(
                        name = "uk_items_business_name",
                        columnNames = {"business_owner_id", "name"}
                )
        }
)
public class Item extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_group_id")
    private ItemGroup itemGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(nullable = false, length = 250)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String sku;

    @Column(length = 100)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(length = 100)
    private String barcode;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;


    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType = ItemType.PHYSICAL;

    @Column(name = "track_inventory", nullable = false, columnDefinition = "boolean default true")
    private Boolean trackInventory = true;

    public boolean isStockTracked() {
        return Boolean.TRUE.equals(trackInventory);
    }

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ItemImage> images = new ArrayList<>();

    @Column(length = 40)
    private String badge;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<DescriptionBlock> descriptionBlocks = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ItemAttribute> attributes;


    /**
     * The colours this item comes in, declared once and shared by every size.
     *
     * Empty on an item that is not sold by colour, which is most of them. A
     * size says which of these it offers; the pair of the two is what carries
     * stock.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ItemColor> colors = new ArrayList<>();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("variantName ASC")
    private List<ItemVariant> variants = new ArrayList<>();


    /**
     * Larger units this item is bought or sold in, each expressed in base
     * units. They belong to the item, not to the unit — see
     * {@link ItemUomConversion}.
     */
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemUomConversion> uomConversions = new ArrayList<>();


    /**
     * Extras this item offers, and whether each is currently on sale here.
     *
     * The add-ons themselves belong to the business library, so the cascade
     * covers only the link: taking one off this item must never delete it out
     * from under every other item using it.
     */
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemAddOn> addOns = new ArrayList<>();

    @Column(name = "low_stock_default", nullable = false, columnDefinition = "int default 20")
    private Integer lowStockDefault = 20;

    /**
     * Whether every channel sells from the whole shelf, or each gets a share.
     *
     * Nullable on purpose: an item that predates allocation has never been
     * asked the question, and reading that as SHARED is what keeps the shop
     * selling exactly as it did. The allocations themselves live in
     * {@code item_channel_stocks} — this only says whether they are in force.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel_stock_mode", length = 20)
    private ChannelStockMode channelStockMode;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "is_available",
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) default 'ACTIVE'"
    )
    private ItemStatus status = ItemStatus.ACTIVE;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
