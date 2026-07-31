package kh.edu.istad.ite.features.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record StockSummaryResponse(
        UUID itemId,
        BigDecimal quantityOnHand,
        UUID lastEntryId,
        LocalDateTime updatedAt
) {
}
