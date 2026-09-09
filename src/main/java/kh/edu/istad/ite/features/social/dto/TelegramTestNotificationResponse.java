package kh.edu.istad.ite.features.social.dto;

public record TelegramTestNotificationResponse(
        boolean success,
        /** Telegram's own error description (e.g. "chat not found", "bot was kicked from the group chat") when success is false. */
        String message
) {
}
