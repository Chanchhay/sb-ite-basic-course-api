package kh.edu.istad.ite.features.channel.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record ToggleItemChannelRequest(
        @NotNull
        Boolean enabled
) {
}
