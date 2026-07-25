package kh.edu.istad.ite.features.inventory.dto;

import kh.edu.istad.ite.shared.enums.StockEntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record StockEntryResponse(
        UUID id,
        UUID businessOwnerId,
        UUID productId,
        StockEntryType entryType,
        BigDecimal quantityChange,
        BigDecimal quantityBefore,
        BigDecimal quantityAfter,
        BigDecimal unitCost,
        Map<String, Object> batchData,
        String referenceType,
        UUID referenceId,
        String referenceNumber,
        String reason,
        String createdBy,
        LocalDateTime createdDate
) {
}
