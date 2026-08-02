package kh.edu.istad.ite.features.channel.dto;

import kh.edu.istad.ite.features.catalog.dto.ItemResponse;
import java.util.UUID;

public record SalesChannelItemResponse(
        UUID itemChannelId,
        ItemResponse item
) {
}
