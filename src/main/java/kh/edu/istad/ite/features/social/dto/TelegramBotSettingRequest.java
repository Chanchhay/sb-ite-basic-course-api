package kh.edu.istad.ite.features.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TelegramBotSettingRequest(
        @NotBlank(message = "botToken cannot be empty")
        @Size(max = 100, message = "botToken must be at most 100 characters")
        String botToken,

        @Size(max = 2000, message = "welcomeMessage must be at most 2000 characters")
        String welcomeMessage,

        @Size(max = 100, message = "notificationChatId must be at most 100 characters")
        String notificationChatId
) {
}
