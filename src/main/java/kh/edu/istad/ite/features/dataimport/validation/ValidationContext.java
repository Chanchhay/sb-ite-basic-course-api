package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.entity.Unit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The shop as it stands, plus what this file has claimed so far.
 *
 * Read once at the start of checking and then answered from memory. The two
 * halves matter equally: a row can clash with something the shop already has,
 * or with an earlier row of the same file, and only checking both catches the
 * spreadsheet that lists the same SKU twice.
 *
 * Names, SKUs and barcodes are all matched without regard to case, because the
 * system a shop is leaving rarely agreed with itself about capitals.
 */
public class ValidationContext {

    private final UUID businessId;

    private final Map<String, UUID> itemGroupIdsByName = new HashMap<>();

    /**
     * Categories that are already somebody's sub-category.
     *
     * Categories go two deep and no further, so one of these can never be
     * named as a parent. Recorded by name only — asking a lazy parent
     * association for its own parent, once per category, is exactly the
     * round-trip storm this class exists to avoid.
     */
    private final Set<String> subGroupNames = new HashSet<>();
    private final Map<String, UUID> unitIdsByName = new HashMap<>();
    private final Map<String, ExistingItem> itemsBySku = new HashMap<>();
    private final Map<String, ExistingItem> itemsByBarcode = new HashMap<>();
    private final Map<String, ExistingItem> itemsByName = new HashMap<>();

    /** Claimed by earlier rows of this same file, mapped to the row that took it. */
    private final Map<String, Integer> seenNames = new LinkedHashMap<>();
    private final Map<String, Integer> seenSkus = new LinkedHashMap<>();
    private final Map<String, Integer> seenBarcodes = new LinkedHashMap<>();
    private final Map<String, Integer> seenGroupNames = new LinkedHashMap<>();

    /** Categories this file will create, so a second row naming one is not a clash. */
    private final Set<String> plannedGroupNames = new HashSet<>();

    /** Items this file will create, so an opening-stock row can find them. */
    private final Set<String> plannedItemKeys = new HashSet<>();

    public ValidationContext(
            UUID businessId,
            List<ItemGroup> itemGroups,
            List<Unit> units,
            List<Item> items,
            Set<UUID> itemIdsWithStockHistory,
            Set<UUID> itemIdsWithVariants
    ) {
        this.businessId = businessId;

        for (ItemGroup group : itemGroups) {
            itemGroupIdsByName.putIfAbsent(key(group.getName()), group.getId());

            if (group.getParent() != null) {
                subGroupNames.add(key(group.getName()));
            }
        }

        for (Unit unit : units) {
            unitIdsByName.putIfAbsent(key(unit.getName()), unit.getId());
            if (unit.getSlug() != null) {
                unitIdsByName.putIfAbsent(key(unit.getSlug()), unit.getId());
            }
            if (unit.getSymbol() != null) {
                unitIdsByName.putIfAbsent(key(unit.getSymbol()), unit.getId());
            }
        }

        for (Item item : items) {
            ExistingItem existing = new ExistingItem(
                    item.getId(),
                    item.getName(),
                    item.getSku(),
                    item.getBarcode(),
                    item.getItemType(),
                    item.isStockTracked(),
                    itemIdsWithStockHistory.contains(item.getId()),
                    itemIdsWithVariants.contains(item.getId())
            );

            itemsByName.putIfAbsent(key(item.getName()), existing);
            if (item.getSku() != null) {
                itemsBySku.putIfAbsent(key(item.getSku()), existing);
            }
            if (item.getBarcode() != null) {
                itemsByBarcode.putIfAbsent(key(item.getBarcode()), existing);
            }
        }
    }

    public UUID businessId() {
        return businessId;
    }

    // --- what the shop already has -------------------------------------------------

    public UUID findItemGroupId(String name) {
        return name == null ? null : itemGroupIdsByName.get(key(name));
    }

    public boolean hasItemGroup(String name) {
        return findItemGroupId(name) != null;
    }

    /** Whether naming this category as a parent would make a third level. */
    public boolean isSubGroup(String name) {
        return name != null && subGroupNames.contains(key(name));
    }

    public UUID findUnitId(String name) {
        return name == null ? null : unitIdsByName.get(key(name));
    }

    public ExistingItem findItemBySku(String sku) {
        return sku == null ? null : itemsBySku.get(key(sku));
    }

    public ExistingItem findItemByBarcode(String barcode) {
        return barcode == null ? null : itemsByBarcode.get(key(barcode));
    }

    public ExistingItem findItemByName(String name) {
        return name == null ? null : itemsByName.get(key(name));
    }

    /**
     * The item a row is about, by whichever identifier it carried.
     *
     * SKU first, then barcode, then name — most reliable to least. A shop that
     * exports both a SKU and a name will be matched on the SKU, so an item
     * that was renamed still lands on the right one.
     */
    public ExistingItem findItem(String sku, String barcode, String name) {
        ExistingItem bySku = findItemBySku(sku);
        if (bySku != null) {
            return bySku;
        }

        ExistingItem byBarcode = findItemByBarcode(barcode);

        return byBarcode != null ? byBarcode : findItemByName(name);
    }

    // --- what this file has claimed so far -----------------------------------------

    /** The earlier row that already took this name, if any. */
    public Integer rowThatTookName(String name) {
        return name == null ? null : seenNames.get(key(name));
    }

    public Integer rowThatTookSku(String sku) {
        return sku == null ? null : seenSkus.get(key(sku));
    }

    public Integer rowThatTookBarcode(String barcode) {
        return barcode == null ? null : seenBarcodes.get(key(barcode));
    }

    public Integer rowThatTookGroupName(String name) {
        return name == null ? null : seenGroupNames.get(key(name));
    }

    public void claimName(String name, int rowNumber) {
        put(seenNames, name, rowNumber);
    }

    public void claimSku(String sku, int rowNumber) {
        put(seenSkus, sku, rowNumber);
    }

    public void claimBarcode(String barcode, int rowNumber) {
        put(seenBarcodes, barcode, rowNumber);
    }

    public void claimGroupName(String name, int rowNumber) {
        put(seenGroupNames, name, rowNumber);
    }

    /**
     * Notes a category this file will bring into being.
     *
     * Every later row naming it is then answered as though it existed, which
     * is what stops a file of two hundred items in five categories reporting
     * a hundred and ninety-five clashes.
     */
    public void planItemGroup(String name) {
        if (name != null) {
            plannedGroupNames.add(key(name));
        }
    }

    public boolean isItemGroupPlanned(String name) {
        return name != null && plannedGroupNames.contains(key(name));
    }

    /**
     * Notes an item this file will create, under every identifier it carries.
     *
     * What lets an opening-stock row refer to an item created a few rows
     * above it in the same file.
     */
    public void planItem(String sku, String barcode, String name) {
        addPlannedItemKey(sku);
        addPlannedItemKey(barcode);
        addPlannedItemKey(name);
    }

    public boolean isItemPlanned(String sku, String barcode, String name) {
        return containsPlannedKey(sku) || containsPlannedKey(barcode) || containsPlannedKey(name);
    }

    private void addPlannedItemKey(String value) {
        if (value != null && !value.isBlank()) {
            plannedItemKeys.add(key(value));
        }
    }

    private boolean containsPlannedKey(String value) {
        return value != null && !value.isBlank() && plannedItemKeys.contains(key(value));
    }

    private void put(Map<String, Integer> claims, String value, int rowNumber) {
        if (value != null && !value.isBlank()) {
            claims.putIfAbsent(key(value), rowNumber);
        }
    }

    private String key(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
