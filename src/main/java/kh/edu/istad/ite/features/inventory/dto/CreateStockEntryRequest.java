package kh.edu.istad.ite.features.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record CreateStockEntryRequest(
        @NotNull(message = "productId cannot be null")
        UUID productId,

        @NotNull(message = "entryType cannot be null")
        @Pattern(regexp = "OPENING_STOCK|STOCK_IN|STOCK_OUT|ADJUSTMENT|SALE|RETURN", message = "Entry type must be one of: OPENING_STOCK, STOCK_IN, STOCK_OUT, ADJUSTMENT, SALE, RETURN")
        String entryType,

        @NotNull(message = "quantityChange cannot be null")
        @Digits(integer = 15, fraction = 3, message = "quantityChange must have at most 15 integer digits and 3 decimal places")
        BigDecimal quantityChange,

        @Digits(integer = 16, fraction = 2, message = "unitCost must have at most 16 integer digits and 2 decimal places")
        BigDecimal unitCost,

        Map<String, Object> batchData,

        @Size(max = 40, message = "referenceType must be at most 40 characters")
        String referenceType,

        UUID referenceId,

        @Size(max = 80, message = "referenceNumber must be at most 80 characters")
        String referenceNumber,

        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason
) {
}
