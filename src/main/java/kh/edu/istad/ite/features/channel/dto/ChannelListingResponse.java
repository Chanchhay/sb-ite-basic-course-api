package kh.edu.istad.ite.features.channel.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Everything one channel does differently, in one read.
 *
 * The items themselves are not repeated here — the screen already has the
 * catalogue and its business prices. What a channel adds is only ever three
 * things: what it sells, what it charges instead, and when it is open.
 */
public record ChannelListingResponse(
        UUID channelId,
        String name,
        String code,
        Boolean active,
        /** The rule every line starts from before its own exception. */
        OverrideDto globalRule,
        /** Null when nobody has set hours, which is read as always open. */
        ChannelScheduleDto schedule,
        /** Whether the channel is taking orders at the moment it was asked. */
        boolean openNow,
        List<UUID> enabledItemIds,
        List<ChannelPriceLineDto> overrides
) {

    public record OverrideDto(String kind, BigDecimal value) {
    }

    /**
     * One exception, on the same line Set Price prices: the item on its own
     * ({@code variantId} and {@code unitId} both null), one of its options, or
     * one of its larger units.
     */
    public record ChannelPriceLineDto(
            UUID itemId,
            UUID variantId,
            UUID unitId,
            String kind,
            BigDecimal value
    ) {
    }
}
