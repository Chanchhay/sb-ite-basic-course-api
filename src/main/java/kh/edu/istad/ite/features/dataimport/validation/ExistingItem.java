package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.shared.enums.ItemType;

import java.util.UUID;

/**
 * What checking needs to know about an item the shop already has.
 *
 * A flat copy rather than the entity, because the checking step holds the
 * whole catalogue at once and reaching through a lazy association per row is
 * how an import of ten thousand rows turns into ten thousand queries.
 */
public record ExistingItem(
        UUID id,
        String name,
        String sku,
        String barcode,
        ItemType itemType,
        boolean trackInventory,
        boolean hasStockHistory,
        boolean soldInOptions
) {
}
