package kh.edu.istad.ite.features.channel.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One channel's share of one option.
 *
 * The channel's name and the option's travel with it so the back office can
 * label a row without a second read, the way the listing endpoint already
 * carries names beside ids.
 */
public record ChannelStockAllocationResponse(
        UUID salesChannelId,
        String channelName,
        String channelCode,
        UUID variantId,
        String variantName,
        BigDecimal quantity,
        /** How much of it has already been sold on that channel. */
        BigDecimal sold
) {
}
