package kh.edu.istad.ite.features.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A margin experiment against the current catalog.
 *
 * Cost and quantity always come from inventory — the caller never sends
 * them — so the numbers a shop plans around can't drift from what's
 * actually on the shelf. Only the margin knobs are the caller's to turn.
 */
public record SaleProfitCalculatorRequest(
        @NotNull
        SaleProfitCalculatorMode mode,

        /** Margin applied to any item without its own entry in {@code itemMargins}. */
        @NotNull
        @PositiveOrZero
        BigDecimal defaultMarginPercent,

        /** Per-item overrides; an item absent here falls back to {@code defaultMarginPercent}. */
        @Valid
        List<ItemMargin> itemMargins,

        /** Required only in {@link SaleProfitCalculatorMode#BUSINESS_TARGET}. */
        BigDecimal targetMarginPercent,

        @NotNull
        @PositiveOrZero
        BigDecimal operatingExpense
) {
    public record ItemMargin(
            @NotNull UUID itemId,
            @NotNull @PositiveOrZero BigDecimal marginPercent
    ) {
    }
}
