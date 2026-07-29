package kh.edu.istad.ite.features.social.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChat(
        Long id,
        String type
) {
}
