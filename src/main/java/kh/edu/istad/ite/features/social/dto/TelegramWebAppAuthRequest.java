package kh.edu.istad.ite.features.social.dto;

import jakarta.validation.constraints.NotBlank;

public record TelegramWebAppAuthRequest(
        @NotBlank String businessId,
        @NotBlank String initData
) {
}
