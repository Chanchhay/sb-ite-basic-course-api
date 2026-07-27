package kh.edu.istad.ite.features.inventory.mapper;

import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class StockEntryMapper {

    public StockEntryResponse toResponse(StockEntry stockEntry) {
        return new StockEntryResponse(
                stockEntry.getId(),
                stockEntry.getBusiness().getId(),
                stockEntry.getItem().getId(),
                stockEntry.getEntryType(),
                stockEntry.getQuantityChange(),
                stockEntry.getQuantityBefore(),
                stockEntry.getQuantityAfter(),
                stockEntry.getUnitCost(),
                stockEntry.getBatchData(),
                stockEntry.getReferenceType(),
                stockEntry.getReferenceId(),
                stockEntry.getReferenceNumber(),
                stockEntry.getReason(),
                stockEntry.getCreatedBy(),
                stockEntry.getCreatedDate()
        );
    }

    public StockSummaryResponse toSummary(StockEntry stockEntry) {
        return new StockSummaryResponse(
                stockEntry.getItem().getId(),
                stockEntry.getQuantityAfter(),
                stockEntry.getId(),
                stockEntry.getCreatedDate()
        );
    }

    public StockSummaryResponse emptySummary(UUID itemId) {
        return new StockSummaryResponse(itemId, BigDecimal.ZERO.setScale(3), null, null);
    }
}
