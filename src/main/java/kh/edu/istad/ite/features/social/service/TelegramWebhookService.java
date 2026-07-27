package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.features.social.telegram.TelegramUpdate;

public interface TelegramWebhookService {

    void handleUpdate(String webhookSecret, String secretTokenHeader, TelegramUpdate update);
}
