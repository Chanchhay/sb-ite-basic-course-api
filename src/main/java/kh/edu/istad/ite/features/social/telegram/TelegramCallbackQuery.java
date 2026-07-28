package kh.edu.istad.ite.features.social.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackQuery(
        String id,
        TelegramFrom from,
        TelegramMessage message,
        String data
) {
}
