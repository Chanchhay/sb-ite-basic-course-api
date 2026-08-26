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
        BigDecimal openingStock
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
}
