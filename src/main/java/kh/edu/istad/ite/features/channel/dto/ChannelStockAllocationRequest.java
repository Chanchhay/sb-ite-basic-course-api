package kh.edu.istad.ite.features.channel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One share, as the back office sets it.
 *
 * {@code sold} is never sent: it is what the shop has already done, not
 * something it can decide, and letting it be typed would let an allocation be
 * refilled without a delivery.
 */
public record ChannelStockAllocationRequest(
        @NotNull(message = "Choose a sales channel.")
        UUID salesChannelId,

        /** The option this share is of. Null on an item with no options. */
        UUID variantId,

        @NotNull(message = "Set how many this channel may sell.")
        @DecimalMin(value = "0", message = "An allocation cannot be negative.")
        BigDecimal quantity
) {
}
