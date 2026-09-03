package kh.edu.istad.ite.features.notification.push.dto;

import jakarta.validation.constraints.NotBlank;

public record UnsubscribePushRequest(
        @NotBlank String endpoint
) {
}
