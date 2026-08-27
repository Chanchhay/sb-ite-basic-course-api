package kh.edu.istad.ite.features.dataimport.canonical;

import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.enums.ItemType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An item, as read from one row.
 *
 * Categories and units are carried by name rather than by id. The row came
 * from a system that never heard of FluxiBiz, so a name is all it can offer;
 * turning that into a category — creating it if the shop does not have it yet
 * — is the commit's job, not the reader's.
 *
 * @param openingStock how many the shop has on hand, when the same file
 *                     carries the count. Null means the file said nothing,
 *                     which is different from a file that said zero.
 * @param costPrice    what one unit cost. The inventory ledger insists on a
 *                     figure for stock arriving, so this is what an opening
 *                     balance is valued at.
 */
public record ItemImportRecord(
        String name,
        String sku,
        String barcode,
        String itemGroupName,
        String unitName,
        ItemType itemType,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPrice,
        String description,
        String badge,
        Boolean trackInventory,
        Integer lowStockLevel,
        ItemStatus status,
        BigDecimal openingStock,

        /**
         * What ties this row to its siblings, when the file lists one row per
         * option. Null on a file of plain items.
         */
        String groupKey,

        /** The option this row describes, or {@link RowOptions#NONE}. */
        RowOptions options,

        String imageUrl
) implements ImportRecord {

    @Override
    public Map<String, Object> normalized() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("sku", sku);
        values.put("barcode", barcode);
        values.put("itemGroup", itemGroupName);
        values.put("unit", unitName);
        values.put("itemType", itemType == null ? null : itemType.name());
        values.put("price", price);
        values.put("compareAtPrice", compareAtPrice);
        values.put("costPrice", costPrice);
        values.put("description", description);
        values.put("badge", badge);
        values.put("trackInventory", trackInventory);
        values.put("lowStockLevel", lowStockLevel);
        values.put("status", status == null ? null : status.name());
        values.put("openingStock", openingStock);

        if (options != null && options.isPresent()) {
            values.put("option", options.label());
        }
        if (groupKey != null) {
            values.put("groupKey", groupKey);
        }
        if (imageUrl != null) {
            values.put("imageUrl", imageUrl);
        }

        return values;
    }

    @Override
    public String externalId() {
        if (sku != null) {
            return sku;
        }

        return barcode != null ? barcode : name;
    }

    /** Whether this row also carries a starting quantity to post. */
    public boolean hasOpeningStock() {
        return openingStock != null;
    }

    /** Whether this row is one option of an item rather than a whole item. */
    public boolean hasOptions() {
        return options != null && options.isPresent();
    }

    /**
     * What this row's item is identified by while the file is being read.
     *
     * The group column when the file has one, and the item's name when it does
     * not — because a variant export that omits a parent code still repeats the
     * item's name on every one of its rows, and that is enough to tell which
     * rows belong together.
     */
    public String groupingKey() {
        if (groupKey != null && !groupKey.isBlank()) {
            return groupKey.trim().toLowerCase();
        }

        return name == null ? null : name.trim().toLowerCase();
    }
}
