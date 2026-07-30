package kh.edu.istad.ite.features.channel.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ItemChannelResponse(
        UUID id,

        UUID itemId,

        String itemName,

        UUID salesChannelId,

        String channelName,

        String channelCode,

        Boolean enabled
) {
}
