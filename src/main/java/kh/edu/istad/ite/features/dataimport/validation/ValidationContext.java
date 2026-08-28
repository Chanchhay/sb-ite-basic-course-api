package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.entity.Unit;

import java.util.ArrayList;
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

    /**
     * Categories that have sub-categories of their own.
     *
     * An item is filed on a leaf, never on a parent — the catalogue refuses it,
     * because a shop that files a bottle under Beverages when Beverages holds
     * Coffee and Tea has told nobody which shelf it is on. Caught here so the
     * row is refused with that explanation rather than at commit, after the
     * shop has already agreed to the import.
     */
    private final Set<String> parentGroupNames = new HashSet<>();

    /**
     * Each parent's sub-categories, spelled as the shop spells them.
     *
     * Used to finish the sentence when a row is refused for being filed on a
     * parent. "Beverages has sub-categories" sends the shop off to look them
     * up; naming them lets the file be fixed in one pass.
     */
    private final Map<String, List<String>> subGroupsByParent = new LinkedHashMap<>();

    /** Which category each sub-category sits under, both as the shop spelled them. */
    private final Map<String, String> parentNameByGroup = new HashMap<>();
    private final Map<String, UUID> unitIdsByName = new HashMap<>();
    private final Map<String, ExistingItem> itemsBySku = new HashMap<>();
    private final Map<String, ExistingItem> itemsByBarcode = new HashMap<>();
    private final Map<String, ExistingItem> itemsByName = new HashMap<>();

    /** Claimed by earlier rows of this same file, mapped to the row that took it. */
    private final Map<String, Integer> seenNames = new LinkedHashMap<>();
    private final Map<String, Integer> seenSkus = new LinkedHashMap<>();
    private final Map<String, Integer> seenBarcodes = new LinkedHashMap<>();
    private final Map<String, Integer> seenGroupNames = new LinkedHashMap<>();

    /**
     * The row that opened each group of option rows, and the option pairs each
     * group has used so far.
     *
     * A variant export repeats the item's name on every one of its rows, so
     * without this the second row of every item would be reported as a
     * duplicate of the first. What must be unique within a group is not the
     * name but the option — one Small/Black per shirt.
     */
    private final Map<String, Integer> groupFirstRow = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> groupOptions = new LinkedHashMap<>();

    /** Categories this file will create, so a second row naming one is not a clash. */
    private final Set<String> plannedGroupNames = new HashSet<>();

    /**
     * Categories this file will give a sub-category to.
     *
     * A file filing one item under "Beverages > Coffee" has made Beverages a
     * parent, whether or not it was one when checking started. A later row
     * putting an item straight onto Beverages has to be refused for the same
     * reason an existing parent would refuse it, or the first import of a
     * hierarchy would half-succeed and strand items on a shelf no screen shows.
     */
    private final Set<String> plannedParentNames = new HashSet<>();

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

        Map<UUID, String> groupNamesById = new HashMap<>();

        for (ItemGroup group : itemGroups) {
            itemGroupIdsByName.putIfAbsent(key(group.getName()), group.getId());
            groupNamesById.put(group.getId(), group.getName());
        }

        for (ItemGroup group : itemGroups) {
            if (group.getParent() == null) {
                continue;
            }

            subGroupNames.add(key(group.getName()));

            // The proxy answers for its id without being loaded, so the parent's
            // name is looked up here rather than fetched one category at a time.
            String parentName = groupNamesById.get(group.getParent().getId());

            if (parentName != null) {
                parentGroupNames.add(key(parentName));
                parentNameByGroup.put(key(group.getName()), parentName);
                subGroupsByParent
                        .computeIfAbsent(key(parentName), ignored -> new ArrayList<>())
                        .add(group.getName());
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

    /** Whether this category holds sub-categories, and so holds no items. */
    public boolean hasSubGroups(String name) {
        return name != null
                && (parentGroupNames.contains(key(name)) || plannedParentNames.contains(key(name)));
    }

    /** This category's sub-categories, named as the shop spelled them. */
    public List<String> subGroupsOf(String parentName) {
        return parentName == null
                ? List.of()
                : List.copyOf(subGroupsByParent.getOrDefault(key(parentName), List.of()));
    }

    /** The category this one sits under, or null if it is top level or unknown. */
    public String parentOf(String name) {
        return name == null ? null : parentNameByGroup.get(key(name));
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

    /**
     * Notes a category this file will create underneath another.
     *
     * Both halves are recorded: the child so later rows naming it are answered
     * as though it existed, and the parent so later rows are told it can no
     * longer hold items directly.
     */
    public void planSubGroup(String name, String parentName) {
        planItemGroup(name);

        if (name == null || parentName == null) {
            return;
        }

        plannedParentNames.add(key(parentName));
        parentNameByGroup.putIfAbsent(key(name), parentName);
        subGroupsByParent
                .computeIfAbsent(key(parentName), ignored -> new ArrayList<>())
                .add(name);
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

    // --- items sold in options -----------------------------------------------------

    /**
     * Records that this row belongs to a group, and answers whether it opened
     * it.
     *
     * The row that opens a group is the one that carries the item — its name,
     * category and unit are what the item is created with, and it is the only
     * row of the group that has to be checked against the existing catalogue.
     */
    public boolean openGroup(String groupKey, int rowNumber) {
        if (groupKey == null) {
            return true;
        }

        Integer first = groupFirstRow.putIfAbsent(key(groupKey), rowNumber);

        return first == null;
    }

    public Integer groupOpenedAt(String groupKey) {
        return groupKey == null ? null : groupFirstRow.get(key(groupKey));
    }

    /**
     * Claims one option within its group, or names the row that already took it.
     *
     * Two rows offering the same size in the same colour are the same shelf
     * twice over; the catalogue refuses the pair outright, so it is caught here
     * where it can be pointed at a row number.
     */
    public Integer claimOption(String groupKey, String optionLabel, int rowNumber) {
        if (groupKey == null || optionLabel == null) {
            return null;
        }

        return groupOptions
                .computeIfAbsent(key(groupKey), ignored -> new LinkedHashMap<>())
                .putIfAbsent(key(optionLabel), rowNumber);
    }

    /** How many items this file would create, counting a group as one. */
    public int distinctGroups() {
        return groupFirstRow.size();
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
