package kh.edu.istad.ite.features.channel.dto;

import kh.edu.istad.ite.shared.enums.ChannelStockMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * How one item's shelf is shared out between its channels.
 *
 * An item nobody has split answers with {@code SHARED} and an empty list
 * rather than a 404: "never split" is a real state, and the back office shows
 * it as the off position of a switch.
 */
public record ItemChannelStockResponse(
        UUID itemId,
        ChannelStockMode mode,
        List<ChannelStockAllocationResponse> allocations,
        LocalDateTime updatedAt
) {
}
