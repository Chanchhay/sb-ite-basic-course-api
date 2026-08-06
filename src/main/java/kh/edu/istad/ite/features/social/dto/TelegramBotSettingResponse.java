package kh.edu.istad.ite.features.social.dto;

import java.util.UUID;

public record TelegramBotSettingResponse(
        UUID id,
        UUID businessId,
        String botUsername,
        Long telegramBotId,
        String welcomeMessage,
        boolean botTokenConfigured,
        boolean active,
        String webhookUrl,
        String notificationChatId
) {
}
