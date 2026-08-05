package kh.edu.istad.ite.features.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSalesChannelRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 50)
        String code,

        Boolean isActive
) {
}
