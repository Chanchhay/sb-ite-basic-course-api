package kh.edu.istad.ite.features.social.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        @JsonProperty("message_id") Integer messageId,
        TelegramChat chat,
        TelegramFrom from,
        String text
) {
}
