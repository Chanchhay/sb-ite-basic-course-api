package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.social.dto.TelegramBotSettingRequest;
import kh.edu.istad.ite.features.social.dto.TelegramBotSettingResponse;

public interface BusinessTelegramBotService {

    TelegramBotSettingResponse getMySetting();

    TelegramBotSettingResponse connect(TelegramBotSettingRequest request);

    TelegramBotSettingResponse activate();

    TelegramBotSettingResponse deactivate();

    void disconnect();

    /** Toggles the bot's menu button between "Open Shop" (Mini App) and Telegram's default commands list. */
    TelegramBotSettingResponse setMiniAppEnabled(boolean enabled);
}
