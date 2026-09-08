package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.social.dto.TelegramBotSettingRequest;
import kh.edu.istad.ite.features.social.dto.TelegramBotSettingResponse;
import kh.edu.istad.ite.features.social.dto.TelegramTestNotificationResponse;

public interface BusinessTelegramBotService {

    TelegramBotSettingResponse getMySetting();

    TelegramBotSettingResponse connect(TelegramBotSettingRequest request);

    TelegramBotSettingResponse activate();

    TelegramBotSettingResponse deactivate();

    void disconnect();

    /** Toggles the bot's menu button between "Open Shop" (Mini App) and Telegram's default commands list. */
    TelegramBotSettingResponse setMiniAppEnabled(boolean enabled);

    /** Sends a real Telegram message to the configured notificationChatId right now, so a wrong chat id or a bot that was kicked from the group surfaces immediately instead of only when a real payment alert silently goes nowhere. */
    TelegramTestNotificationResponse testNotification();
}
