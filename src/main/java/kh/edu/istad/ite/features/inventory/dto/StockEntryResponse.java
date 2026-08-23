package kh.edu.istad.ite.features.inventory.dto;

import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import kh.edu.istad.ite.shared.enums.StockEntryType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        /** The lot and dates this movement was recorded against. */
        String lotNumber,
        LocalDate manufacturedAt,
        LocalDate expiresAt,
        Map<String, Object> batchData,
        String referenceType,
        UUID referenceId,
        String referenceNumber,
        String reason,
        /**
         * The batches this movement drew from, on the way out.
         *
         * Empty on the way in, where nothing has been consumed, and empty on
         * the list endpoints — reading it for every row would be a query per
         * movement to answer a question only the opened one is asking.
         */
        List<StockConsumptionResponse> consumedBatches,
        String createdBy,
        LocalDateTime createdDate
) {
}
