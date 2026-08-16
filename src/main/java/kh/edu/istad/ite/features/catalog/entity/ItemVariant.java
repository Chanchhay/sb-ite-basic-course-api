package kh.edu.istad.ite.features.catalog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import kh.edu.istad.ite.config.audit.BasedAuditingEntity;
import kh.edu.istad.ite.features.business.entity.Business;
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
@Table(name = "item_variants")
public class ItemVariant extends BasedAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_owner_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, length = 255)
    private String slug;

    @Column(name = "variant_name", length = 150)
    private String variantName;

    // Each variation is scanned and counted on its own, so it carries its own
    // SKU and barcode rather than borrowing the item's.
    @Column(length = 100)
    private String sku;

    @Column(length = 100)
    private String barcode;

    // What the storefront shows while this option is the one picked — a black
    // phone next to a white one. The picture itself is uploaded to the asset
    // store before the item is saved, so this is only ever the URL it came
    // back as. Empty on an option that looks like every other one.
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * The swatch this option shows — the circle a shopper clicks.
     *
     * Set only on an option that is a colour; a size has nothing to show. It
     * belongs to the option rather than to a separate axis because the option
     * is what carries the price and the shelf, and a colour a shop cannot
     * count is not one it can sell.
     */
    /**
     * The size half of what this row is — "Large".
     *
     * A variant is a size and a colour together, because that pair is what a
     * shop counts: Large is out in Navy while Large in Red is stacked up, and
     * one row for "Large" could not say so. {@link #variantName} stays the
     * readable form of the pair ("Large / Red"), so receipts, tickets and
     * stock reports keep printing a name rather than two fields.
     */
    @Column(name = "option_name", length = 150)
    private String optionName;

    /**
     * Which of the item's colours this row is, by its value. Null on an item
     * that is not sold by colour, where the size alone is the whole variant.
     */
    @Column(name = "color_value", length = 150)
    private String colorValue;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean available = true;

}
