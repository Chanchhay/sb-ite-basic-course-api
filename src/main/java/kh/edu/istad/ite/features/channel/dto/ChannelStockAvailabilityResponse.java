package kh.edu.istad.ite.features.channel.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one channel may still sell of one thing.
 *
 * Only ever sent for items the shop has actually split. An item on SHARED has
 * no ceiling to report, and sending "the whole shelf" for every item in the
 * catalogue would be a page of numbers the till already has.
 *
 * {@code available} is the allocation less what the channel has sold — not
 * clamped to the shelf, because the caller is holding the shelf figure already
 * and the smaller of the two is what it shows.
 */
public record ChannelStockAvailabilityResponse(
        UUID itemId,
        UUID variantId,
        BigDecimal available
) {
}
