package kh.edu.istad.ite.features.dataimport.canonical;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A starting quantity for an item that already exists, as read from one row.
 *
 * Which item is named three ways because different old systems export
 * different ones. They are tried in the order they are declared here: a SKU is
 * the shop's own code and the most reliable, a barcode is next, and the name
 * is a last resort because two things can share one.
 */
public record OpeningStockImportRecord(
        String sku,
        String barcode,
        String itemName,
        BigDecimal quantity,
        BigDecimal unitCost
) implements ImportRecord {

    @Override
    public Map<String, Object> normalized() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sku", sku);
        values.put("barcode", barcode);
        values.put("itemName", itemName);
        values.put("quantity", quantity);
        values.put("unitCost", unitCost);
        return values;
    }

    @Override
    public String externalId() {
        if (sku != null) {
            return sku;
        }

        return barcode != null ? barcode : itemName;
    }
}
