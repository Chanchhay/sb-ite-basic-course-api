package kh.edu.istad.ite.features.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CreateStockEntryRequest(
        /** Exactly one of itemId and addOnId. */
        UUID itemId,

        UUID addOnId,

        /**
         * Which option of the item moved. Only ever set alongside an itemId,
         * and only on an item that has options. Left out, the movement is
         * against the item as a whole.
         */
        UUID variantId,

        @NotNull(message = "entryType cannot be null")
        @Pattern(regexp = "OPENING_STOCK|STOCK_IN|STOCK_OUT|ADJUSTMENT|SALE|RETURN", message = "Entry type must be one of: OPENING_STOCK, STOCK_IN, STOCK_OUT, ADJUSTMENT, SALE, RETURN")
        String entryType,

        @NotNull(message = "quantityChange cannot be null")
        @Digits(integer = 15, fraction = 3, message = "quantityChange must have at most 15 integer digits and 3 decimal places")
        BigDecimal quantityChange,

        @Digits(integer = 16, fraction = 2, message = "unitCost must have at most 16 integer digits and 2 decimal places")
        BigDecimal unitCost,

        @Digits(integer = 16, fraction = 2, message = "unitSalePrice must have at most 16 integer digits and 2 decimal places")
        BigDecimal unitSalePrice,

        @Digits(integer = 15, fraction = 3, message = "enteredQuantity must have at most 15 integer digits and 3 decimal places")
        BigDecimal enteredQuantity,

        UUID unitId,

        /**
         * The supplier's reference for this delivery, so a recall can be
         * answered with more than a date.
         */
        @Size(max = 80, message = "lotNumber must be at most 80 characters")
        String lotNumber,

        LocalDate manufacturedAt,

        /**
         * When this delivery goes off.
         *
         * What the consumption queue is ordered by before anything else — a
         * short-dated delivery leaves before older stock that keeps longer.
         * Left out, the batch is treated as one that does not expire.
         */
        LocalDate expiresAt,

        /**
         * When the stock actually arrived, if that is not now.
         *
         * A delivery recorded two days late still belongs where it happened in
         * the queue, or stock that arrived after it would be sold first.
         */
        LocalDateTime receivedAt,

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
