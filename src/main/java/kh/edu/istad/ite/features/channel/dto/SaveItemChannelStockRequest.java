package kh.edu.istad.ite.features.channel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.shared.enums.ChannelStockMode;

import java.util.List;

/**
 * The split, replaced whole.
 *
 * Sent as one piece because it is decided as one: a mode and the shares that
 * go with it. The list replaces what was there — a channel left out of it has
 * no share — so an empty list says "nobody has one" rather than "leave things
 * as they were".
 */
public record SaveItemChannelStockRequest(
        @NotNull(message = "Say whether this item's stock is shared or allocated.")
        ChannelStockMode mode,

        @Valid
        List<ChannelStockAllocationRequest> allocations
) {
}
