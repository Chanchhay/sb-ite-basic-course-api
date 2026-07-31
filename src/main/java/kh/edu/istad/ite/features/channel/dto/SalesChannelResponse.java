package kh.edu.istad.ite.features.channel.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record SalesChannelResponse(

        UUID id,

        String name,

        String code,

        Boolean isActive

) {
}
