package kh.edu.istad.ite.features.notification.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record SubscribePushRequest(
        @NotBlank String endpoint,
        @Valid Keys keys,
        Long expirationTime
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {
    }
}
