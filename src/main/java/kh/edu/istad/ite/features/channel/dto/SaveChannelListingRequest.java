package kh.edu.istad.ite.features.channel.dto;

import kh.edu.istad.ite.features.channel.dto.ChannelListingResponse.ChannelPriceLineDto;
import kh.edu.istad.ite.features.channel.dto.ChannelListingResponse.OverrideDto;

import java.util.List;
import java.util.UUID;

/**
 * A channel saved whole, exactly as the screen shows it.
 *
 * Everything is optional so a partial save leaves the rest alone; a field that
 * is sent replaces what was there. Sending an empty list is therefore how you
 * say "nothing" — a channel that sells nothing, or one that makes no
 * exceptions — which a null could never distinguish from "unchanged".
 */
public record SaveChannelListingRequest(
        OverrideDto globalRule,
        ChannelScheduleDto schedule,
        List<UUID> enabledItemIds,
        List<ChannelPriceLineDto> overrides
) {
}
