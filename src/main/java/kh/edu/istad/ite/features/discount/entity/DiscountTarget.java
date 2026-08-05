package kh.edu.istad.ite.features.discount.entity;

import jakarta.persistence.*;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.shared.enums.DiscountTargetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Links a discount to the specific item (product) or item group (category)
// it applies to. Only used when the owning discount's scope is ITEM or
// CATEGORY - order-level discounts (scope = ORDER) don't need any targets.
// Exactly one of "item" / "itemGroup" is populated, matching targetType.
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "discount_targets")
public class DiscountTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id", nullable = false)
    private Discount discount;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type" , nullable = false, length = 20)
    private DiscountTargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_group_id")
    private ItemGroup itemGroup;

}
