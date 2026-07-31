package kh.edu.istad.ite.features.channel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateItemChannelRequest(
        @NotNull
        UUID itemId,
        @NotNull
        UUID salesChannelId
) {
}
