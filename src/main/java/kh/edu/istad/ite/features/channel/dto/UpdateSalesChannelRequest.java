package kh.edu.istad.ite.features.channel.dto;

import jakarta.validation.constraints.Size;

public record UpdateSalesChannelRequest(
        @Size(max = 100)
        String name,

        @Size(max = 50)
        String code,

        Boolean isActive
) {
}
