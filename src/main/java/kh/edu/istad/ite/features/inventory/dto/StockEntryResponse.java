package kh.edu.istad.ite.features.inventory.dto;

import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import kh.edu.istad.ite.shared.enums.StockEntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record StockEntryResponse(
        UUID id,
        UUID businessOwnerId,
        UUID itemId,
        UUID addOnId,
        /** The option that moved, when the item is sold in options. */
        UUID variantId,
        /**
         * Its name, carried so the ledger still reads back after an option is
         * renamed or removed from the item.
         */
        String variantName,
        StockEntryType entryType,
        BigDecimal quantityChange,
        BigDecimal quantityBefore,
        BigDecimal quantityAfter,
        BigDecimal unitCost,
        BigDecimal costOfGoods,
        BigDecimal unitSalePrice,
        BigDecimal enteredQuantity,
        UnitResponse enteredUnit,
        Map<String, Object> batchData,
        String referenceType,
        UUID referenceId,
        String referenceNumber,
        String reason,
        String createdBy,
        LocalDateTime createdDate
) {
}
