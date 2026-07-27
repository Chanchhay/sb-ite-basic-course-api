package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.social.dto.TelegramBotSettingRequest;
import kh.edu.istad.ite.features.social.dto.TelegramBotSettingResponse;

public interface BusinessTelegramBotService {

    TelegramBotSettingResponse getMySetting();

    TelegramBotSettingResponse connect(TelegramBotSettingRequest request);

    TelegramBotSettingResponse activate();

    TelegramBotSettingResponse deactivate();

    void disconnect();
}
